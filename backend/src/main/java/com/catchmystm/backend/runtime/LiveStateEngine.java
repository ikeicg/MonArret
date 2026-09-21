package com.catchmystm.backend.runtime;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.catchmystm.backend.runtime.model.StopTimeContext;
import com.catchmystm.backend.service.StmPoller;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
public class LiveStateEngine {

	private final Flux<FeedMessage> tripUpdatesFeed;
	private final Flux<FeedMessage> vehiclePositionsFeed;

	private final ActiveTripStateStore tripStateStore;
	private final GeometricOperationsService geoOpsService;
	private final Clock montrealClock;

	private final Sinks.Many<LiveTripUpdate> sink = Sinks.many().multicast().directBestEffort();

	public LiveStateEngine(StmPoller poller, ActiveTripStateStore tripStateStore,
			GeometricOperationsService geoOpsService, Clock montrealClock) {
		tripUpdatesFeed = poller.getTripUpdatesPublisher();
		vehiclePositionsFeed = poller.getVehiclePositionsPublisher();
		this.tripStateStore = tripStateStore;
		this.geoOpsService = geoOpsService;
		this.montrealClock = montrealClock;
	}

	@EventListener(ApplicationReadyEvent.class)
	private void start() {

		tripUpdatesFeed
			.publishOn(Schedulers.boundedElastic())
			.subscribe(feedMessage -> {
				handleTripUpdates(feedMessage);
			});

		vehiclePositionsFeed
			.publishOn(Schedulers.boundedElastic())
			.subscribe(feedMessage -> {
				handleVehiclePositions(feedMessage);
			});

	}

	// Package-private (not private) so tests in this package can call it directly
	// without going through the reactive subscription.
	void handleTripUpdates(FeedMessage feed) {

		if(feed == null) return;

		for(FeedEntity entity : feed.getEntityList()) {

			if(!entity.hasTripUpdate()) continue;

			try {
				processTripUpdate(entity.getTripUpdate());
			} catch (Exception e) {
				log.warn("Failed to process trip update (entity {}): {}", entity.getId(), e.getMessage());
			}

		}

	}

	private void processTripUpdate(TripUpdate tripUpdate) {

		TripDescriptor trip = tripUpdate.getTrip();
		String tripId = trip.getTripId();
		if (tripId == null || tripId.isBlank()) return;

		String scheduleRelationship = trip.getScheduleRelationship().name();
		boolean scheduled = "SCHEDULED".equals(scheduleRelationship);
		String routeId = trip.hasRouteId() ? trip.getRouteId() : null;
		Integer directionId = trip.hasDirectionId() ? trip.getDirectionId() : null;
		List<TripUpdate.StopTimeUpdate> stopTimeUpdates = tripUpdate.getStopTimeUpdateList();

		tripStateStore.applyTripUpdate(tripId, scheduled, routeId, directionId, state -> {
			state.setScheduleRelationship(scheduleRelationship);
			applyStopTimeUpdates(state, stopTimeUpdates);
			return state;
		});
	}

	// stopSchedule is null on a bare (not-yet-SCHEDULED) state — nothing to apply yet.
	//
	// Plain nested scan, deliberately not order-dependent: `updates`' ordering is STM's
	// convention, not a GTFS-RT spec guarantee, so matching can't rely on it. At this project's
	// scale (a few dozen stops per trip) the O(n*m) cost is negligible.
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

				LocalTime estimatedArrival = update.hasArrival() && update.getArrival().hasTime()
						? toLocalTime(update.getArrival().getTime())
						: current.estimatedArrival();

				LocalTime estimatedDeparture = update.hasDeparture() && update.getDeparture().hasTime()
						? toLocalTime(update.getDeparture().getTime())
						: current.estimatedDeparture();

				schedule.set(i, new StopTimeContext(
						current.stopId(),
						current.stopSequence(),
						current.scheduledArrival(),
						estimatedArrival,
						current.scheduledDeparture(),
						estimatedDeparture,
						current.x(),
						current.y(),
						update.getScheduleRelationship().name()));

				matched++;
				break;
			}
		}

		if (matched < updates.size()) {
			log.warn("Trip {}: only matched {}/{} stop_time_update entries against the static schedule",
					state.getTripId(), matched, updates.size());
		}
	}

	private LocalTime toLocalTime(long epochSeconds) {
		return LocalTime.ofInstant(Instant.ofEpochSecond(epochSeconds), montrealClock.getZone());
	}

	// Package-private (not private) so tests in this package can call it directly
	// without going through the reactive subscription.
	void handleVehiclePositions(FeedMessage feed) {

		if(feed == null) return;

		for(FeedEntity entity : feed.getEntityList()) {

			if(!entity.hasVehicle()) continue;

			try {
				processVehiclePosition(entity.getVehicle());
			} catch (Exception e) {
				log.warn("Failed to process vehicle position (entity {}): {}", entity.getId(), e.getMessage());
			}

		}
	}

	private void processVehiclePosition(VehiclePosition vehicle) {

		if (!vehicle.hasTrip()) return;

		String tripId = vehicle.getTrip().getTripId();
		if (tripId == null || tripId.isBlank()) return;

		if (!vehicle.hasPosition()) return;

		Position position = vehicle.getPosition();
		ProjCoordinate projected = geoOpsService.transformWgsToUtm(position.getLongitude(), position.getLatitude());

		Instant pingTime = vehicle.hasTimestamp()
				? Instant.ofEpochSecond(vehicle.getTimestamp())
				: Instant.now();

		tripStateStore.updateTripState(tripId, state -> {

			state.setLastX(projected.x);
			state.setLastY(projected.y);

			if (position.hasSpeed()) {
				state.setCurrentSpeedMps(position.getSpeed());
			}
			if (vehicle.hasCurrentStopSequence()) {
				state.setCurrentStopSequence(vehicle.getCurrentStopSequence());
			}
			if (vehicle.hasStopId()) {
				state.setCurrentStopId(vehicle.getStopId());
			}
			if (vehicle.hasCurrentStatus()) {
				state.setCurrentStatus(vehicle.getCurrentStatus().name());
			}

			state.addGpsPing(projected.x, projected.y, pingTime);
			state.gpsPingTouch(pingTime);

			return state;
		});
	}


	private void publishUpdate(LiveTripUpdate tripUpdate) {
		if(tripUpdate != null) {
			sink.tryEmitNext(tripUpdate);
		}
	}

	public Flux<LiveTripUpdate> getPublisher(){
		return sink.asFlux();
	}

}
