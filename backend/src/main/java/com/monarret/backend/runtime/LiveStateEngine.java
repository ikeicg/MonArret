package com.monarret.backend.runtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.monarret.backend.runtime.model.StopTimeContext;
import com.monarret.backend.runtime.model.TripSnapshot;
import com.monarret.backend.service.StmPoller;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
public class LiveStateEngine {

	private final Flux<FeedMessage> tripUpdatesFeed;
	private final Flux<FeedMessage> vehiclePositionsFeed;

	private final ActiveTripStateStore tripStateStore;
	private final GeometricOperationsService geoOpsService;
	// How far outside its scheduled times a trip can still be running (late buses, early starts).
	private static final Duration RUNNING_WINDOW_MARGIN = Duration.ofHours(2);

	private final Clock montrealClock;
	private final IngestMetrics tripUpdatesMetrics;
	private final IngestMetrics vehiclePositionsMetrics;
	private final Counter tripUpdatesDropped;
	private final Counter vehiclePositionsDropped;

	private final AtomicLong lastTripUpdatesHeaderTimestamp = new AtomicLong(-1);
	private final AtomicLong lastVehiclePositionsHeaderTimestamp = new AtomicLong(-1);

	// Header timestamps of the newest messages whose entities are fully ingested. Unlike the two above,
	// these only move once the store reflects the message.
	private final AtomicLong ingestedTripUpdatesTimestamp = new AtomicLong(-1);
	private final AtomicLong ingestedVehiclePositionsTimestamp = new AtomicLong(-1);

	public LiveStateEngine(StmPoller poller, ActiveTripStateStore tripStateStore,
			GeometricOperationsService geoOpsService, Clock montrealClock, MeterRegistry meterRegistry) {
		tripUpdatesFeed = poller.getTripUpdatesPublisher();
		vehiclePositionsFeed = poller.getVehiclePositionsPublisher();
		this.tripStateStore = tripStateStore;
		this.geoOpsService = geoOpsService;
		this.montrealClock = montrealClock;
		this.tripUpdatesMetrics = new IngestMetrics("tripUpdates", "trip update", meterRegistry);
		this.vehiclePositionsMetrics = new IngestMetrics("vehiclePositions", "vehicle position", meterRegistry);
		this.tripUpdatesDropped = droppedCounter(meterRegistry, "tripUpdates");
		this.vehiclePositionsDropped = droppedCounter(meterRegistry, "vehiclePositions");
	}

	@EventListener(ApplicationReadyEvent.class)
	private void start() {
		subscribeTo(tripUpdatesFeed, "trip updates", tripUpdatesDropped, this::handleTripUpdates);
		subscribeTo(vehiclePositionsFeed, "vehicle positions", vehiclePositionsDropped, this::handleVehiclePositions);
	}

	// If ingestion falls behind, only the newest message waits; older ones are counted and skipped.
	// A failure while ingesting one message is logged and does not end the subscription.
	Disposable subscribeTo(Flux<FeedMessage> feed, String name, Counter dropped, Consumer<FeedMessage> handler) {
		return latestWins(feed, dropped::increment)
			.publishOn(Schedulers.boundedElastic(), 1)
			.subscribe(message -> {
				try {
					handler.accept(message);
				} catch (Exception e) {
					log.error("Failed to ingest a {} message: {}", name, e.toString());
				}
			});
	}

	static <T> Flux<T> latestWins(Flux<T> source, Runnable onDropped) {
		return source.onBackpressureBuffer(1, dropped -> onDropped.run(), BufferOverflowStrategy.DROP_OLDEST);
	}

	private static Counter droppedCounter(MeterRegistry registry, String feed) {
		return Counter.builder("feed.dropped")
			.description("Feed messages skipped because ingestion was still busy with an earlier one")
			.tag("feed", feed)
			.register(registry);
	}

	// A repeated or older header timestamp is skipped. A message without one is never skipped.
	private static boolean isRepeatedMessage(FeedMessage feed, AtomicLong newestHeaderTimestamp) {
		if (!feed.getHeader().hasTimestamp()) return false;
		long timestamp = feed.getHeader().getTimestamp();
		return timestamp <= newestHeaderTimestamp.getAndAccumulate(timestamp, Math::max);
	}

	// Identifies the live data the store currently holds: changes after each ingested message of either feed.
	public String feedVersion() {
		return ingestedTripUpdatesTimestamp.get() + "-" + ingestedVehiclePositionsTimestamp.get();
	}

	// Milliseconds the newest ingested message took, or -1 before the first one.
	public long lastTripUpdatesIngestMillis() {
		return tripUpdatesMetrics.lastMillis();
	}

	public long lastVehiclePositionsIngestMillis() {
		return vehiclePositionsMetrics.lastMillis();
	}

	// Milliseconds the first message after startup took (it builds the trip states), or -1 before it.
	public long firstTripUpdatesIngestMillis() {
		return tripUpdatesMetrics.firstMillis();
	}

	public long firstVehiclePositionsIngestMillis() {
		return vehiclePositionsMetrics.firstMillis();
	}

	private long ingestedStamp(FeedMessage feed) {
		return feed.getHeader().hasTimestamp() ? feed.getHeader().getTimestamp() : montrealClock.instant().getEpochSecond();
	}

	void handleTripUpdates(FeedMessage feed) {

		if(feed == null) return;
		if(isRepeatedMessage(feed, lastTripUpdatesHeaderTimestamp)) return;

		long startedAt = System.nanoTime();

		for(FeedEntity entity : feed.getEntityList()) {

			if(!entity.hasTripUpdate()) continue;

			try {
				processTripUpdate(entity.getTripUpdate());
			} catch (TripNotFoundException e) {
				log.debug("Skipping trip update (entity {}): {}", entity.getId(), e.getMessage());
			} catch (Exception e) {
				log.warn("Failed to process trip update (entity {}): {}", entity.getId(), e.getMessage());
			}

		}

		ingestedTripUpdatesTimestamp.set(ingestedStamp(feed));
		tripUpdatesMetrics.record(feed.getEntityCount(), startedAt);
	}

	private void processTripUpdate(TripUpdate tripUpdate) {

		TripDescriptor trip = tripUpdate.getTrip();
		String tripId = trip.getTripId();
		if (tripId == null || tripId.isBlank()) return;

		LocalDate serviceDate = parseServiceDate(trip);
		if (serviceDate == null) {
			log.debug("Skipping trip update for {}: missing or invalid start_date", tripId);
			return;
		}

		String scheduleRelationship = trip.getScheduleRelationship().name();
		boolean scheduled = "SCHEDULED".equals(scheduleRelationship);
		String routeId = trip.hasRouteId() ? trip.getRouteId() : null;
		Integer directionId = trip.hasDirectionId() ? trip.getDirectionId() : null;
		List<TripUpdate.StopTimeUpdate> stopTimeUpdates = tripUpdate.getStopTimeUpdateList();

		tripStateStore.applyTripUpdate(tripId, serviceDate, scheduled, routeId, directionId, state -> {
			state.setScheduleRelationship(scheduleRelationship);
			applyStopTimeUpdates(state, stopTimeUpdates);
			return state;
		});
	}

	// stopSchedule is null on a bare (not-yet-SCHEDULED) state — nothing to apply yet.
	//
	// Plain nested scan, not order-dependent: updates' ordering is STM's
	// convention, not a guarantee, so matching can't rely on it. O(n*m) cost is negligible.
	private void applyStopTimeUpdates(ActiveTripState state, List<TripUpdate.StopTimeUpdate> updates) {

		List<StopTimeContext> schedule = state.getStopSchedule();
		if (schedule == null || updates.isEmpty()) return;

		int matched = 0;

		for (TripUpdate.StopTimeUpdate update : updates) {

			if (!update.hasStopSequence()) continue;
			int stopSequence = update.getStopSequence();

			for (int i = 0; i < schedule.size(); i++) {

				StopTimeContext current = schedule.get(i);
				if (current.stopSequence() != stopSequence) continue;

				boolean skipped = update.getScheduleRelationship() == TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED;

				Instant estimatedArrival = skipped ? null
						: update.hasArrival() && update.getArrival().hasTime()
						? Instant.ofEpochSecond(update.getArrival().getTime())
						: current.estimatedArrival();

				Instant estimatedDeparture = skipped ? null
						: update.hasDeparture() && update.getDeparture().hasTime()
						? Instant.ofEpochSecond(update.getDeparture().getTime())
						: current.estimatedDeparture();

				schedule.set(i, new StopTimeContext(
						current.stopId(),
						current.stopName(),
						current.stopSequence(),
						current.scheduledArrival(),
						estimatedArrival,
						current.scheduledDeparture(),
						estimatedDeparture,
						current.x(),
						current.y(),
						current.distanceAlongRoute(),
						update.getScheduleRelationship().name()));

				matched++;
				break;
			}
		}

		if (matched < updates.size()) {
			log.debug("Trip {}: only matched {}/{} stop_time_update entries against the static schedule",
					state.getTripId(), matched, updates.size());
		}
	}

	// start_date is the trip's service date (YYYYMMDD); null when absent or malformed.
	private LocalDate parseServiceDate(TripDescriptor trip) {
		if (!trip.hasStartDate()) return null;
		try {
			return LocalDate.parse(trip.getStartDate(), DateTimeFormatter.BASIC_ISO_DATE);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	
	void handleVehiclePositions(FeedMessage feed) {

		if(feed == null) return;
		if(isRepeatedMessage(feed, lastVehiclePositionsHeaderTimestamp)) return;

		long startedAt = System.nanoTime();

		for(FeedEntity entity : feed.getEntityList()) {

			if(!entity.hasVehicle()) continue;

			try {
				processVehiclePosition(entity.getVehicle());
			} catch (TripNotFoundException e) {
				log.debug("Skipping vehicle position (entity {}): {}", entity.getId(), e.getMessage());
			} catch (Exception e) {
				log.warn("Failed to process vehicle position (entity {}): {}", entity.getId(), e.getMessage());
			}

		}

		ingestedVehiclePositionsTimestamp.set(ingestedStamp(feed));
		vehiclePositionsMetrics.record(feed.getEntityCount(), startedAt);
	}

	private void processVehiclePosition(VehiclePosition vehicle) {

		if (!vehicle.hasTrip()) return;

		String tripId = vehicle.getTrip().getTripId();
		if (tripId == null || tripId.isBlank()) return;

		if (!vehicle.hasPosition()) return;

		if (!vehicle.hasTimestamp()) {
			log.warn("Skipping vehicle position for {}: missing timestamp", tripId);
			return;
		}

		LocalDate serviceDate = parseServiceDate(vehicle.getTrip());
		if (serviceDate == null) {
			log.debug("Skipping vehicle position for {}: missing or invalid start_date", tripId);
			return;
		}

		Position position = vehicle.getPosition();
		if (!isRealPlace(position.getLatitude(), position.getLongitude())) {
			log.debug("Skipping vehicle position for {}: coordinates are not a real place", tripId);
			return;
		}
		ProjCoordinate projected = geoOpsService.transformWgsToUtm(position.getLongitude(), position.getLatitude());

		Instant pingTime = Instant.ofEpochSecond(vehicle.getTimestamp());
		serviceDate = serviceDateOfRunningTrip(tripId, serviceDate, pingTime);

		tripStateStore.updateTripState(tripId, serviceDate, state -> {

			Double alongRoute = projectAlongRoute(state, vehicle, projected.x, projected.y);

			Double offRoute = state.getRoute() == null ? null
					: geoOpsService.distanceFromRoute(state.getRoute(), projected.x, projected.y);

			if (!state.addGpsPing(projected.x, projected.y, pingTime, alongRoute, offRoute)) {
				return state;
			}

			state.updatePositionFrozen(position.hasSpeed() ? position.getSpeed() : null);

			if (vehicle.hasCurrentStopSequence()) {
				state.setCurrentStopSequence(vehicle.getCurrentStopSequence());
			}
			if (vehicle.hasStopId()) {
				state.setCurrentStopId(vehicle.getStopId());
			}
			if (vehicle.hasCurrentStatus()) {
				state.setCurrentStatus(vehicle.getCurrentStatus().name());
			}
			if (vehicle.hasOccupancyStatus()) {
				state.setOccupancyStatus(vehicle.getOccupancyStatus().name());
			}

			return state;
		});
	}


	// After midnight, vehiclePositions name the calendar date while tripUpdates name the service date, so a trip
	// scheduled at 25:55 appears with dates a day apart. A position dated the day after the state we hold, and
	// inside that trip's scheduled window, belongs to the same running trip; anything else is a new instance.
	private LocalDate serviceDateOfRunningTrip(String tripId, LocalDate reported, Instant at) {
		return tripStateStore.snapshotTrip(tripId)
			.filter(trip -> trip.serviceDate() != null && trip.serviceDate().plusDays(1).equals(reported))
			.filter(trip -> scheduleWindowContains(trip, at))
			.map(TripSnapshot::serviceDate)
			.orElse(reported);
	}

	private static boolean scheduleWindowContains(TripSnapshot trip, Instant at) {
		List<StopTimeContext> schedule = trip.stopSchedule();
		if (schedule == null || schedule.isEmpty()) return false;
		StopTimeContext first = schedule.get(0);
		StopTimeContext last = schedule.get(schedule.size() - 1);
		Instant start = first.scheduledDeparture() != null ? first.scheduledDeparture() : first.scheduledArrival();
		Instant end = last.scheduledArrival() != null ? last.scheduledArrival() : last.scheduledDeparture();
		if (start == null || end == null) return false;
		return !at.isBefore(start.minus(RUNNING_WINDOW_MARGIN)) && !at.isAfter(end.plus(RUNNING_WINDOW_MARGIN));
	}

	private static boolean isRealPlace(float latitude, float longitude) {
		return Float.isFinite(latitude) && Float.isFinite(longitude)
				&& Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
	}

	// Bounded to the stretch between the stops either side of the vehicle's current stop, so a route that
	// revisits a place can't snap the ping to the wrong pass. Falls back to the whole route when the
	// message has no stop sequence or it isn't in the schedule; null when the state has no route yet.
	private Double projectAlongRoute(ActiveTripState state, VehiclePosition vehicle, double x, double y) {

		LineString route = state.getRoute();
		if (route == null) return null;

		List<StopTimeContext> schedule = state.getStopSchedule();
		if (schedule != null && vehicle.hasCurrentStopSequence()) {

			for (int i = 0; i < schedule.size(); i++) {
				if (schedule.get(i).stopSequence() != vehicle.getCurrentStopSequence()) continue;

				double lo = i > 0 ? schedule.get(i - 1).distanceAlongRoute() : 0;
				double hi = i < schedule.size() - 1
						? schedule.get(i + 1).distanceAlongRoute()
						: state.getRouteLengthMeters();
				return geoOpsService.distanceAlongRouteBetween(route, x, y, lo, hi);
			}
		}

		return geoOpsService.distanceAlongRoute(route, x, y);
	}

}
