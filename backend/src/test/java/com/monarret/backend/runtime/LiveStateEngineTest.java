package com.monarret.backend.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.Mock;
import org.locationtech.proj4j.ProjCoordinate;
import org.mockito.junit.jupiter.MockitoExtension;

import com.monarret.backend.entity.Shape;
import com.monarret.backend.entity.ShapeId;
import com.monarret.backend.entity.Stop;
import com.monarret.backend.entity.StopTime;
import com.monarret.backend.entity.StopTimeId;
import com.monarret.backend.entity.Trip;
import com.monarret.backend.repository.ShapeRepository;
import com.monarret.backend.repository.TripRepository;
import com.monarret.backend.runtime.model.StopTimeContext;
import com.monarret.backend.runtime.model.TripSnapshot;
import com.monarret.backend.service.StmPoller;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class LiveStateEngineTest {

	@Mock
	private TripRepository tripRepository;

	@Mock
	private ShapeRepository shapeRepository;

	@Mock
	private StmPoller poller;

	private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
	private static final String SERVICE_DATE = "20260921";
	private static final long EPOCH_T0 = Instant.parse("2026-09-21T11:55:00Z").getEpochSecond();

	private GeometricOperationsService geoOps;
	private ActiveTripStateStore store;
	private Clock fixedClock;
	private LiveStateEngine engine;
	private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

	@BeforeEach
	void setUp() {
		GeometricOperationsService geoOpsService = new GeometricOperationsService();
		geoOps = geoOpsService;
		fixedClock = Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneId.of("America/Montreal"));
		ActiveTripStateFactory factory = new ActiveTripStateFactory(tripRepository, shapeRepository, geoOpsService,
				fixedClock);
		store = new ActiveTripStateStore(factory);

		when(poller.getTripUpdatesPublisher()).thenReturn(Flux.empty());
		when(poller.getVehiclePositionsPublisher()).thenReturn(Flux.empty());

		engine = new LiveStateEngine(poller, store, geoOpsService, fixedClock, meters);
	}

	// --- handleTripUpdates ---

	@Test
	void canceledTripNotYetTracked_getsBareState_andSkipsDatabase() {

		TripUpdate update = tripUpdate("trip-1", TripDescriptor.ScheduleRelationship.CANCELED, "10", 0);

		engine.handleTripUpdates(feedWithTripUpdate(update));

		ActiveTripState state = store.fetchTripState("trip-1").orElseThrow();
		assertThat(state.getScheduleRelationship()).isEqualTo("CANCELED");
		assertThat(state.getRouteId()).isEqualTo("10");
		assertThat(state.getDirectionId()).isEqualTo(0);
		assertThat(state.getStopSchedule()).isNull();
		assertThat(state.getRoute()).isNull();

		verify(tripRepository, never()).findById(any());
	}

	@Test
	void scheduledTripNotYetTracked_buildsFullState() {

		stubStaticSchedule("trip-2");

		TripUpdate update = tripUpdate("trip-2", TripDescriptor.ScheduleRelationship.SCHEDULED, "10", 0);

		engine.handleTripUpdates(feedWithTripUpdate(update));

		ActiveTripState state = store.fetchTripState("trip-2").orElseThrow();
		assertThat(state.getScheduleRelationship()).isEqualTo("SCHEDULED");
		assertThat(state.getStopSchedule()).hasSize(2);
		assertThat(state.getRoute()).isNotNull();

		verify(tripRepository, times(1)).findById("trip-2");
	}

	// --- feed message deduplication ---

	@Test
	void tripUpdatesMessage_withRepeatedHeaderTimestamp_isSkipped() {

		engine.handleTripUpdates(withHeaderTimestamp(feedWithTripUpdate(
				tripUpdate("trip-d1", TripDescriptor.ScheduleRelationship.CANCELED, "10", 0)), 1000));

		engine.handleTripUpdates(withHeaderTimestamp(feedWithTripUpdate(
				tripUpdate("trip-d1", TripDescriptor.ScheduleRelationship.SCHEDULED, "10", 0)), 1000));

		assertThat(store.fetchTripState("trip-d1").orElseThrow().getScheduleRelationship()).isEqualTo("CANCELED");
		verify(tripRepository, never()).findById("trip-d1");
	}

	@Test
	void eachIngestedMessage_isTimedOnce_andARepeatedMessageIsNot() {

		FeedMessage message = withHeaderTimestamp(feedWithTripUpdate(
				tripUpdate("trip-m1", TripDescriptor.ScheduleRelationship.CANCELED, "10", 0)), 1000);

		assertThat(engine.lastTripUpdatesIngestMillis()).isEqualTo(-1);

		engine.handleTripUpdates(message);
		engine.handleTripUpdates(message);

		assertThat(meters.get("feed.ingest").tag("feed", "tripUpdates").timer().count()).isEqualTo(1);
		assertThat(engine.lastTripUpdatesIngestMillis()).isGreaterThanOrEqualTo(0);
		assertThat(engine.firstTripUpdatesIngestMillis()).isGreaterThanOrEqualTo(0);
		assertThat(engine.lastVehiclePositionsIngestMillis()).isEqualTo(-1);
	}

	@Test
	void tripUpdatesMessage_withNewHeaderTimestamp_isProcessed() {

		engine.handleTripUpdates(withHeaderTimestamp(feedWithTripUpdate(
				tripUpdate("trip-d2", TripDescriptor.ScheduleRelationship.CANCELED, "10", 0)), 1000));

		stubStaticSchedule("trip-d2");
		engine.handleTripUpdates(withHeaderTimestamp(feedWithTripUpdate(
				tripUpdate("trip-d2", TripDescriptor.ScheduleRelationship.SCHEDULED, "10", 0)), 1020));

		assertThat(store.fetchTripState("trip-d2").orElseThrow().getScheduleRelationship()).isEqualTo("SCHEDULED");
	}

	@Test
	void vehiclePositionsMessage_withRepeatedHeaderTimestamp_isSkipped() {

		stubStaticSchedule("trip-d3");
		engine.handleVehiclePositions(withHeaderTimestamp(feedWithVehicle(
				vehicleAt("trip-d3", 45.52f, -73.57f).setTimestamp(EPOCH_T0).build()), 1000));

		engine.handleVehiclePositions(withHeaderTimestamp(feedWithVehicle(
				vehicleAt("trip-d3", 45.53f, -73.58f).setTimestamp(EPOCH_T0 + 20).build()), 1000));

		assertThat(store.fetchTripState("trip-d3").orElseThrow().getRecentPings()).hasSize(1);
	}

	@Test
	void messageWithAnOlderHeaderTimestamp_isSkipped() {

		stubStaticSchedule("trip-d6");
		engine.handleVehiclePositions(withHeaderTimestamp(feedWithVehicle(
				vehicleAt("trip-d6", 45.52f, -73.57f).setTimestamp(EPOCH_T0 + 20).build()), 1020));

		engine.handleVehiclePositions(withHeaderTimestamp(feedWithVehicle(
				vehicleAt("trip-d6", 45.53f, -73.58f).setTimestamp(EPOCH_T0 + 40).build()), 1000));

		assertThat(store.fetchTripState("trip-d6").orElseThrow().getRecentPings()).hasSize(1);
	}

	@Test
	void messagesWithoutAHeaderTimestamp_areNeverSkipped() {

		stubStaticSchedule("trip-d4");
		engine.handleVehiclePositions(feedWithVehicle(
				vehicleAt("trip-d4", 45.52f, -73.57f).setTimestamp(EPOCH_T0).build()));
		engine.handleVehiclePositions(feedWithVehicle(
				vehicleAt("trip-d4", 45.53f, -73.58f).setTimestamp(EPOCH_T0 + 20).build()));

		assertThat(store.fetchTripState("trip-d4").orElseThrow().getRecentPings()).hasSize(2);
	}

	@Test
	void theTwoFeeds_trackTheirHeaderTimestampsSeparately() {

		stubStaticSchedule("trip-d5");
		engine.handleTripUpdates(withHeaderTimestamp(feedWithTripUpdate(
				tripUpdate("trip-d5", TripDescriptor.ScheduleRelationship.SCHEDULED, "10", 0)), 1000));

		engine.handleVehiclePositions(withHeaderTimestamp(feedWithVehicle(
				vehicleAt("trip-d5", 45.52f, -73.57f).setTimestamp(EPOCH_T0).build()), 1000));

		assertThat(store.fetchTripState("trip-d5").orElseThrow().getRecentPings()).hasSize(1);
	}

	@Test
	void bareTripLaterBecomesScheduled_getsUpgradedToFull() {

		TripUpdate canceled = tripUpdate("trip-3", TripDescriptor.ScheduleRelationship.CANCELED, "10", 0);
		engine.handleTripUpdates(feedWithTripUpdate(canceled));
		assertThat(store.fetchTripState("trip-3").orElseThrow().getStopSchedule()).isNull();

		stubStaticSchedule("trip-3");
		TripUpdate scheduled = tripUpdate("trip-3", TripDescriptor.ScheduleRelationship.SCHEDULED, "10", 0);
		engine.handleTripUpdates(feedWithTripUpdate(scheduled));

		ActiveTripState state = store.fetchTripState("trip-3").orElseThrow();
		assertThat(state.getStopSchedule()).hasSize(2);
		assertThat(state.getScheduleRelationship()).isEqualTo("SCHEDULED");
	}

	@Test
	void alreadyFullState_isNotRecreated_sameObjectUpdatedInPlace() {

		stubStaticSchedule("trip-4");

		engine.handleTripUpdates(feedWithTripUpdate(
				tripUpdate("trip-4", TripDescriptor.ScheduleRelationship.SCHEDULED, "10", 0)));
		ActiveTripState afterFirst = store.fetchTripState("trip-4").orElseThrow();

		engine.handleTripUpdates(feedWithTripUpdate(
				tripUpdate("trip-4", TripDescriptor.ScheduleRelationship.SCHEDULED, "10", 0)));
		ActiveTripState afterSecond = store.fetchTripState("trip-4").orElseThrow();

		assertThat(afterSecond).isSameAs(afterFirst);
		verify(tripRepository, times(1)).findById("trip-4");
	}

	@Test
	void stopTimeUpdate_matchesCorrectStopBySequence_andUpdatesEstimatedTimes() {

		stubStaticSchedule("trip-5");

		long arrivalEpoch = Instant.parse("2026-09-21T12:05:00Z").getEpochSecond();

		TripUpdate update = TripUpdate.newBuilder()
				.setTrip(TripDescriptor.newBuilder()
						.setTripId("trip-5").setStartDate(SERVICE_DATE)
						.setScheduleRelationship(TripDescriptor.ScheduleRelationship.SCHEDULED)
						.build())
				.addStopTimeUpdate(TripUpdate.StopTimeUpdate.newBuilder()
						.setStopSequence(2)
						.setArrival(com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
								.setTime(arrivalEpoch)
								.build())
						.setScheduleRelationship(TripUpdate.StopTimeUpdate.ScheduleRelationship.SCHEDULED)
						.build())
				.build();

		engine.handleTripUpdates(feedWithTripUpdate(update));

		List<StopTimeContext> schedule = store.fetchTripState("trip-5").orElseThrow().getStopSchedule();

		StopTimeContext stop2 = schedule.stream().filter(s -> s.stopSequence() == 2).findFirst().orElseThrow();
		assertThat(stop2.scheduleRelationship()).isEqualTo("SCHEDULED");
		assertThat(stop2.estimatedArrival())
				.isEqualTo(Instant.ofEpochSecond(arrivalEpoch));

		StopTimeContext stop1 = schedule.stream().filter(s -> s.stopSequence() == 1).findFirst().orElseThrow();
		assertThat(stop1.scheduleRelationship()).isNull();
		assertThat(stop1.estimatedArrival()).isNull();
		assertThat(stop1.estimatedDeparture()).isNull();
	}

	@Test
	void skippedStop_hasItsEstimatedTimesCleared() {

		stubStaticSchedule("trip-5b");

		TripUpdate withEstimate = tripUpdateForStop2("trip-5b",
				TripUpdate.StopTimeUpdate.ScheduleRelationship.SCHEDULED, 1_000_000L);
		engine.handleTripUpdates(feedWithTripUpdate(withEstimate));

		TripUpdate skipped = tripUpdateForStop2("trip-5b",
				TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED, 1_000_300L);
		engine.handleTripUpdates(feedWithTripUpdate(skipped));

		StopTimeContext stop2 = store.fetchTripState("trip-5b").orElseThrow().getStopSchedule().stream()
				.filter(s -> s.stopSequence() == 2).findFirst().orElseThrow();
		assertThat(stop2.scheduleRelationship()).isEqualTo("SKIPPED");
		assertThat(stop2.estimatedArrival()).isNull();
		assertThat(stop2.estimatedDeparture()).isNull();
	}

	@Test
	void skippedStop_leftOutOfLaterUpdate_staysSkipped() {

		stubStaticSchedule("trip-5c");

		engine.handleTripUpdates(feedWithTripUpdate(tripUpdateForStop2("trip-5c",
				TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED, 1_000_000L)));

		TripUpdate withoutStop2 = TripUpdate.newBuilder()
				.setTrip(TripDescriptor.newBuilder()
						.setTripId("trip-5c").setStartDate(SERVICE_DATE)
						.setScheduleRelationship(TripDescriptor.ScheduleRelationship.SCHEDULED)
						.build())
				.addStopTimeUpdate(TripUpdate.StopTimeUpdate.newBuilder().setStopSequence(1).build())
				.build();
		engine.handleTripUpdates(feedWithTripUpdate(withoutStop2));

		StopTimeContext stop2 = store.fetchTripState("trip-5c").orElseThrow().getStopSchedule().stream()
				.filter(s -> s.stopSequence() == 2).findFirst().orElseThrow();
		assertThat(stop2.scheduleRelationship()).isEqualTo("SKIPPED");
	}

	@Test
	void skippedStop_reinstatedByLaterUpdate_becomesScheduledAgain() {

		stubStaticSchedule("trip-5d");

		engine.handleTripUpdates(feedWithTripUpdate(tripUpdateForStop2("trip-5d",
				TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED, 1_000_000L)));
		engine.handleTripUpdates(feedWithTripUpdate(tripUpdateForStop2("trip-5d",
				TripUpdate.StopTimeUpdate.ScheduleRelationship.SCHEDULED, 1_000_600L)));

		StopTimeContext stop2 = store.fetchTripState("trip-5d").orElseThrow().getStopSchedule().stream()
				.filter(s -> s.stopSequence() == 2).findFirst().orElseThrow();
		assertThat(stop2.scheduleRelationship()).isEqualTo("SCHEDULED");
		assertThat(stop2.estimatedArrival()).isEqualTo(Instant.ofEpochSecond(1_000_600L));
	}

	private TripUpdate tripUpdateForStop2(String tripId,
			TripUpdate.StopTimeUpdate.ScheduleRelationship relationship, long arrivalEpoch) {
		return TripUpdate.newBuilder()
				.setTrip(TripDescriptor.newBuilder()
						.setTripId(tripId).setStartDate(SERVICE_DATE)
						.setScheduleRelationship(TripDescriptor.ScheduleRelationship.SCHEDULED)
						.build())
				.addStopTimeUpdate(TripUpdate.StopTimeUpdate.newBuilder()
						.setStopSequence(2)
						.setArrival(TripUpdate.StopTimeEvent.newBuilder().setTime(arrivalEpoch).build())
						.setScheduleRelationship(relationship)
						.build())
				.build();
	}

	@Test
	void oneThrowingEntity_doesNotStopProcessingOfTheRest() {

		when(tripRepository.findById("trip-bad")).thenReturn(Optional.empty());
		stubStaticSchedule("trip-good");

		FeedMessage feed = FeedMessage.newBuilder()
				.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").build())
				.addEntity(FeedEntity.newBuilder().setId("1")
						.setTripUpdate(tripUpdate("trip-bad", TripDescriptor.ScheduleRelationship.SCHEDULED, "10", 0))
						.build())
				.addEntity(FeedEntity.newBuilder().setId("2")
						.setTripUpdate(tripUpdate("trip-good", TripDescriptor.ScheduleRelationship.SCHEDULED, "10", 0))
						.build())
				.build();

		assertThatCode(() -> engine.handleTripUpdates(feed)).doesNotThrowAnyException();

		assertThat(store.fetchTripState("trip-bad")).isEmpty();
		assertThat(store.fetchTripState("trip-good")).isPresent();
	}

	// --- handleVehiclePositions ---

	@Test
	void vehiclePositionWithNoTrip_isSkipped_noException() {

		VehiclePosition vehicle = VehiclePosition.newBuilder()
				.setPosition(Position.newBuilder().setLatitude(45.5f).setLongitude(-73.6f).build())
				.build();

		FeedMessage feed = FeedMessage.newBuilder()
				.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").build())
				.addEntity(FeedEntity.newBuilder().setId("1").setVehicle(vehicle).build())
				.build();

		assertThatCode(() -> engine.handleVehiclePositions(feed)).doesNotThrowAnyException();
		assertThat(store.getActiveTripCount()).isZero();
	}

	@Test
	void vehiclePosition_usesFeedTimestamp_notProcessingTime() {

		stubStaticSchedule("trip-6");

		long feedEpoch = Instant.parse("2026-09-21T11:55:00Z").getEpochSecond();

		VehiclePosition vehicle = VehiclePosition.newBuilder()
				.setTrip(TripDescriptor.newBuilder().setTripId("trip-6").setStartDate(SERVICE_DATE).build())
				.setPosition(Position.newBuilder().setLatitude(45.5f).setLongitude(-73.6f).setSpeed(10f).build())
				.setTimestamp(feedEpoch)
				.build();

		FeedMessage feed = FeedMessage.newBuilder()
				.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").build())
				.addEntity(FeedEntity.newBuilder().setId("1").setVehicle(vehicle).build())
				.build();

		engine.handleVehiclePositions(feed);

		ActiveTripState state = store.fetchTripState("trip-6").orElseThrow();
		assertThat(state.getLatestPing().timestamp()).isEqualTo(Instant.ofEpochSecond(feedEpoch));
	}

	@Test
	void publishedSpeedWithNoMovement_marksThePositionFrozen() {

		stubStaticSchedule("trip-22");

		engine.handleVehiclePositions(feedWithVehicle(
				vehicleWithSpeed("trip-22", 45.52f, -73.57f, EPOCH_T0, 8f)));
		engine.handleVehiclePositions(feedWithVehicle(
				vehicleWithSpeed("trip-22", 45.52f, -73.57f, EPOCH_T0 + 20, 8f)));

		assertThat(store.fetchTripState("trip-22").orElseThrow().isPositionFrozen()).isTrue();
	}

	@Test
	void publishedSpeedWithRealMovement_isNotFrozen() {

		stubStaticSchedule("trip-23");

		engine.handleVehiclePositions(feedWithVehicle(
				vehicleWithSpeed("trip-23", 45.520f, -73.570f, EPOCH_T0, 8f)));
		engine.handleVehiclePositions(feedWithVehicle(
				vehicleWithSpeed("trip-23", 45.522f, -73.572f, EPOCH_T0 + 20, 8f)));

		assertThat(store.fetchTripState("trip-23").orElseThrow().isPositionFrozen()).isFalse();
	}

	@Test
	void stoppedBusPublishingZeroSpeed_isNotFrozen() {

		stubStaticSchedule("trip-24");

		engine.handleVehiclePositions(feedWithVehicle(
				vehicleWithSpeed("trip-24", 45.52f, -73.57f, EPOCH_T0, 0f)));
		engine.handleVehiclePositions(feedWithVehicle(
				vehicleWithSpeed("trip-24", 45.52f, -73.57f, EPOCH_T0 + 20, 0f)));

		assertThat(store.fetchTripState("trip-24").orElseThrow().isPositionFrozen()).isFalse();
	}

	@Test
	void pingFarFromTheRoute_storesItsDistanceFromTheRoute() {

		stubStaticSchedule("trip-25");

		// the fake route runs from (-73.57, 45.52) to (-73.58, 45.53); this point is ~1 km north-west of it
		engine.handleVehiclePositions(feedWithVehicle(
				vehicleAt("trip-25", 45.5300f, -73.5700f).setTimestamp(EPOCH_T0).build()));
		engine.handleVehiclePositions(feedWithVehicle(
				vehicleAt("trip-25", 45.5250f, -73.5750f).setTimestamp(EPOCH_T0 + 20).build()));

		ActiveTripState state = store.fetchTripState("trip-25").orElseThrow();
		assertThat(state.getRecentPings().peekFirst().offRouteMeters()).isGreaterThan(500);
		assertThat(state.getLatestPing().offRouteMeters()).isLessThan(5);
	}


	// --- start_date / service date ---

	@Test
	void tripUpdateWithoutStartDate_isSkipped() {

		TripUpdate update = TripUpdate.newBuilder()
				.setTrip(TripDescriptor.newBuilder()
						.setTripId("trip-7")
						.setScheduleRelationship(TripDescriptor.ScheduleRelationship.CANCELED)
						.build())
				.build();

		engine.handleTripUpdates(feedWithTripUpdate(update));

		assertThat(store.getActiveTripCount()).isZero();
	}

	@Test
	void vehiclePositionWithoutStartDate_isSkipped() {

		VehiclePosition vehicle = VehiclePosition.newBuilder()
				.setTrip(TripDescriptor.newBuilder().setTripId("trip-7").build())
				.setPosition(Position.newBuilder().setLatitude(45.52f).setLongitude(-73.57f).build())
				.setTimestamp(EPOCH_T0)
				.build();

		engine.handleVehiclePositions(feedWithVehicle(vehicle));

		assertThat(store.getActiveTripCount()).isZero();
	}

	@Test
	void extendedClockTime_landsOnTheNextCalendarDay() {

		// 25:30:00 of service day 2026-09-21 is 01:30 on the 22nd, Montreal (EDT, UTC-4)
		stubStaticScheduleWithTimes("trip-8", 91800, 92000);

		engine.handleTripUpdates(feedWithTripUpdate(
				tripUpdate("trip-8", TripDescriptor.ScheduleRelationship.SCHEDULED, "10", 0)));

		StopTimeContext stop1 = store.fetchTripState("trip-8").orElseThrow().getStopSchedule().get(0);
		assertThat(stop1.scheduledArrival()).isEqualTo(Instant.parse("2026-09-22T05:30:00Z"));
	}

	@Test
	void scheduledTimeOnDstFallBackDay_countsFromNoonMinusTwelveHours() {

		// 2026-11-01: clocks go back at 02:00, so noon minus 12h is 01:00 EST (05:00Z), not midnight (04:00Z)
		stubStaticScheduleWithTimes("trip-9", 3600, 3700);

		engine.handleTripUpdates(feedWithTripUpdate(
				tripUpdate("trip-9", TripDescriptor.ScheduleRelationship.SCHEDULED, "10", 0, "20261101")));

		StopTimeContext stop1 = store.fetchTripState("trip-9").orElseThrow().getStopSchedule().get(0);
		assertThat(stop1.scheduledArrival()).isEqualTo(Instant.parse("2026-11-01T06:00:00Z"));
	}

	@Test
	void newServiceDateForSameTripId_rebuildsTheState() {

		stubStaticSchedule("trip-10");

		engine.handleTripUpdates(feedWithTripUpdate(
				tripUpdate("trip-10", TripDescriptor.ScheduleRelationship.SCHEDULED, "10", 0)));
		ActiveTripState firstRun = store.fetchTripState("trip-10").orElseThrow();

		engine.handleTripUpdates(feedWithTripUpdate(
				tripUpdate("trip-10", TripDescriptor.ScheduleRelationship.SCHEDULED, "10", 0, "20260922")));
		ActiveTripState secondRun = store.fetchTripState("trip-10").orElseThrow();

		assertThat(secondRun).isNotSameAs(firstRun);
		assertThat(secondRun.getServiceDate()).isEqualTo(LocalDate.of(2026, 9, 22));
		verify(tripRepository, times(2)).findById("trip-10");
	}

	@Test
	void aBusSittingStill_hasItsStationaryClockStartAtTheFirstPing() {
		stubStaticSchedule("sit-1");

		for (int i = 0; i <= 15; i++) {
			engine.handleVehiclePositions(feedWithVehicle(vehicleAt("sit-1", 45.525f, -73.575f)
					.setTimestamp(EPOCH_T0 + i * 20L).build()));
		}

		TripSnapshot trip = store.snapshotTrip("sit-1").orElseThrow();
		assertThat(trip.stationarySince()).isEqualTo(Instant.ofEpochSecond(EPOCH_T0));
		assertThat(trip.latestPing().timestamp()).isEqualTo(Instant.ofEpochSecond(EPOCH_T0 + 300));
	}

	// --- route and stop geometry ---

	@Test
	void route_isBuiltInUtmMeters_notDegrees() {

		stubStaticSchedule("trip-11");

		engine.handleTripUpdates(feedWithTripUpdate(
				tripUpdate("trip-11", TripDescriptor.ScheduleRelationship.SCHEDULED, "10", 0)));

		ProjCoordinate expectedStart = geoOps.transformWgsToUtm(-73.57, 45.52);
		Coordinate start = store.fetchTripState("trip-11").orElseThrow().getRoute().getCoordinateN(0);

		assertThat(start.x).isCloseTo(expectedStart.x, within(1e-6));
		assertThat(start.y).isCloseTo(expectedStart.y, within(1e-6));
	}

	@Test
	void stopDistancesAlongRoute_increaseInSequenceOrder_andSpanTheRoute() {

		stubStaticSchedule("trip-12");

		engine.handleTripUpdates(feedWithTripUpdate(
				tripUpdate("trip-12", TripDescriptor.ScheduleRelationship.SCHEDULED, "10", 0)));

		ActiveTripState state = store.fetchTripState("trip-12").orElseThrow();
		StopTimeContext stop1 = state.getStopSchedule().get(0);
		StopTimeContext stop2 = state.getStopSchedule().get(1);

		// the fake stops sit on the shape's two end points
		assertThat(stop1.distanceAlongRoute()).isCloseTo(0, within(1.0));
		assertThat(stop2.distanceAlongRoute()).isCloseTo(state.getRouteLengthMeters(), within(1.0));
	}

	// --- ping distance along route and ping filtering ---

	@Test
	void pingAtRouteMidpoint_getsHalfTheRouteLength_withAndWithoutStopSequence() {

		stubStaticSchedule("trip-13");

		// float lat/lon is only accurate to about a metre, hence the tolerance
		engine.handleVehiclePositions(feedWithVehicle(
				vehicleAt("trip-13", 45.525f, -73.575f).setTimestamp(EPOCH_T0).build()));
		engine.handleVehiclePositions(feedWithVehicle(
				vehicleAt("trip-13", 45.525f, -73.575f).setTimestamp(EPOCH_T0 + 20).setCurrentStopSequence(1).build()));

		ActiveTripState state = store.fetchTripState("trip-13").orElseThrow();
		double half = state.getRouteLengthMeters() / 2;

		assertThat(state.getRecentPings()).hasSize(2);
		state.getRecentPings().forEach(ping -> assertThat(ping.alongRoute()).isCloseTo(half, within(3.0)));
	}

	@Test
	void pingOnBareState_hasNoAlongRouteValue() {

		engine.handleTripUpdates(feedWithTripUpdate(
				tripUpdate("trip-14", TripDescriptor.ScheduleRelationship.CANCELED, "10", 0)));

		engine.handleVehiclePositions(feedWithVehicle(
				vehicleAt("trip-14", 45.525f, -73.575f).setTimestamp(EPOCH_T0).build()));

		ActiveTripState state = store.fetchTripState("trip-14").orElseThrow();
		assertThat(state.getRecentPings()).hasSize(1);
		assertThat(state.getLatestPing().alongRoute()).isNull();
		verify(tripRepository, never()).findById(any());
	}

	@Test
	void vehiclePositionWithoutTimestamp_isSkipped() {

		engine.handleVehiclePositions(feedWithVehicle(vehicleAt("trip-15", 45.525f, -73.575f).build()));

		assertThat(store.getActiveTripCount()).isZero();
	}

	@Test
	void repeatedPingTimestamp_leavesTheStateUntouched() {

		stubStaticSchedule("trip-16");

		engine.handleVehiclePositions(feedWithVehicle(
				vehicleAt("trip-16", 45.52f, -73.57f).setTimestamp(EPOCH_T0).build()));
		ActiveTripState state = store.fetchTripState("trip-16").orElseThrow();
		double firstX = state.getLatestPing().x();

		VehiclePosition repeated = vehicleAt("trip-16", 45.53f, -73.58f).setTimestamp(EPOCH_T0).build();
		engine.handleVehiclePositions(feedWithVehicle(repeated));

		assertThat(state.getRecentPings()).hasSize(1);
		assertThat(state.getLatestPing().x()).isEqualTo(firstX);
	}

	@Test
	void shapeId_isTakenFromTheStaticTrip() {

		stubStaticSchedule("trip-21");

		engine.handleVehiclePositions(feedWithVehicle(
				vehicleAt("trip-21", 45.52f, -73.57f).setTimestamp(EPOCH_T0).build()));

		assertThat(store.fetchTripState("trip-21").orElseThrow().getShapeId()).isEqualTo("shape-1");
	}

	@Test
	void occupancyStatus_isStoredFromTheVehiclePosition() {

		stubStaticSchedule("trip-17");

		engine.handleVehiclePositions(feedWithVehicle(
				vehicleAt("trip-17", 45.52f, -73.57f).setTimestamp(EPOCH_T0)
						.setOccupancyStatus(VehiclePosition.OccupancyStatus.FEW_SEATS_AVAILABLE).build()));

		assertThat(store.fetchTripState("trip-17").orElseThrow().getOccupancyStatus())
				.isEqualTo("FEW_SEATS_AVAILABLE");
	}

	@Test
	void laterPingWithoutOccupancy_keepsThePreviousValue() {

		stubStaticSchedule("trip-18");

		engine.handleVehiclePositions(feedWithVehicle(
				vehicleAt("trip-18", 45.52f, -73.57f).setTimestamp(EPOCH_T0)
						.setOccupancyStatus(VehiclePosition.OccupancyStatus.STANDING_ROOM_ONLY).build()));
		engine.handleVehiclePositions(feedWithVehicle(
				vehicleAt("trip-18", 45.521f, -73.571f).setTimestamp(EPOCH_T0 + 20).build()));

		ActiveTripState state = store.fetchTripState("trip-18").orElseThrow();
		assertThat(state.getRecentPings()).hasSize(2);
		assertThat(state.getOccupancyStatus()).isEqualTo("STANDING_ROOM_ONLY");
	}

	@Test
	void repeatedPing_cannotOverwriteOccupancy() {

		stubStaticSchedule("trip-19");

		engine.handleVehiclePositions(feedWithVehicle(
				vehicleAt("trip-19", 45.52f, -73.57f).setTimestamp(EPOCH_T0)
						.setOccupancyStatus(VehiclePosition.OccupancyStatus.MANY_SEATS_AVAILABLE).build()));
		engine.handleVehiclePositions(feedWithVehicle(
				vehicleAt("trip-19", 45.52f, -73.57f).setTimestamp(EPOCH_T0)
						.setOccupancyStatus(VehiclePosition.OccupancyStatus.FULL).build()));

		assertThat(store.fetchTripState("trip-19").orElseThrow().getOccupancyStatus())
				.isEqualTo("MANY_SEATS_AVAILABLE");
	}

	@Test
	void occupancyOnBareState_isStoredWithoutDatabaseWork() {

		engine.handleTripUpdates(feedWithTripUpdate(
				tripUpdate("trip-20", TripDescriptor.ScheduleRelationship.CANCELED, "10", 0)));
		engine.handleVehiclePositions(feedWithVehicle(
				vehicleAt("trip-20", 45.525f, -73.575f).setTimestamp(EPOCH_T0)
						.setOccupancyStatus(VehiclePosition.OccupancyStatus.FULL).build()));

		assertThat(store.fetchTripState("trip-20").orElseThrow().getOccupancyStatus()).isEqualTo("FULL");
		verify(tripRepository, never()).findById(any());
	}

	// --- helpers ---

	// --- feedVersion ---

	@Test
	void feedVersion_movesOnceAMessageIsIngested_andNotOnARepeat() {

		String before = engine.feedVersion();
		FeedMessage message = withHeaderTimestamp(feedWithTripUpdate(
				tripUpdate("trip-1", TripDescriptor.ScheduleRelationship.CANCELED, "10", 0)), EPOCH_T0);

		engine.handleTripUpdates(message);
		String afterFirst = engine.feedVersion();
		engine.handleTripUpdates(message);

		assertThat(afterFirst).isNotEqualTo(before);
		assertThat(engine.feedVersion()).isEqualTo(afterFirst);
	}

	@Test
	void feedVersion_changesWhenEitherFeedAdvances() {

		engine.handleTripUpdates(withHeaderTimestamp(emptyFeed(), EPOCH_T0));
		String afterTripUpdates = engine.feedVersion();

		engine.handleVehiclePositions(withHeaderTimestamp(emptyFeed(), EPOCH_T0));
		String afterVehicles = engine.feedVersion();

		engine.handleTripUpdates(withHeaderTimestamp(emptyFeed(), EPOCH_T0 + 20));

		assertThat(afterVehicles).isNotEqualTo(afterTripUpdates);
		assertThat(engine.feedVersion()).isNotEqualTo(afterVehicles);
	}

	// --- idempotency: ingesting the same content again leaves the state exactly as it was ---

	@Nested
	class Idempotency {

		private TripUpdate scheduledWithEstimate(String tripId) {
			return TripUpdate.newBuilder()
					.setTrip(TripDescriptor.newBuilder().setTripId(tripId).setStartDate(SERVICE_DATE)
							.setScheduleRelationship(TripDescriptor.ScheduleRelationship.SCHEDULED)
							.setRouteId("10").setDirectionId(0).build())
					.addStopTimeUpdate(TripUpdate.StopTimeUpdate.newBuilder()
							.setStopSequence(2)
							.setArrival(TripUpdate.StopTimeEvent.newBuilder().setTime(EPOCH_T0 + 600).build())
							.setScheduleRelationship(TripUpdate.StopTimeUpdate.ScheduleRelationship.SCHEDULED)
							.build())
					.build();
		}

		private VehiclePosition position(String tripId, long timestamp) {
			return vehicleAt(tripId, 45.525f, -73.575f)
					.setTimestamp(timestamp)
					.setCurrentStopSequence(1)
					.setCurrentStatus(VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO)
					.setOccupancyStatus(VehiclePosition.OccupancyStatus.FEW_SEATS_AVAILABLE)
					.build();
		}

		// lastSeenInFeed moves on every sighting by design: it keeps a trip that is seen again from being evicted
		private void assertSameData(TripSnapshot actual, TripSnapshot expected) {
			assertThat(actual).usingRecursiveComparison().ignoringFields("lastSeenInFeed").isEqualTo(expected);
		}

		private TripSnapshot snapshot(String tripId) {
			return store.snapshotTrip(tripId).orElseThrow();
		}

		@Test
		void theSameTripUpdate_ingestedTwice_leavesTheSnapshotUnchanged() {
			stubStaticSchedule("idem-1");
			FeedMessage message = feedWithTripUpdate(scheduledWithEstimate("idem-1"));

			engine.handleTripUpdates(message);
			TripSnapshot afterOnce = snapshot("idem-1");
			engine.handleTripUpdates(message);

			assertSameData(snapshot("idem-1"), afterOnce);
		}

		@Test
		void theSameVehiclePosition_ingestedTwice_isOnePing() {
			stubStaticSchedule("idem-2");
			FeedMessage message = feedWithVehicle(position("idem-2", EPOCH_T0));

			engine.handleVehiclePositions(message);
			TripSnapshot afterOnce = snapshot("idem-2");
			engine.handleVehiclePositions(message);

			assertSameData(snapshot("idem-2"), afterOnce);
			assertThat(snapshot("idem-2").recentPings()).hasSize(1);
		}

		@Test
		void replayingBothFeeds_manyTimes_endsInTheSameState() {
			stubStaticSchedule("idem-3");
			FeedMessage updates = feedWithTripUpdate(scheduledWithEstimate("idem-3"));
			FeedMessage positions = feedWithVehicle(position("idem-3", EPOCH_T0));

			engine.handleTripUpdates(updates);
			engine.handleVehiclePositions(positions);
			TripSnapshot afterOneRound = snapshot("idem-3");

			for (int round = 0; round < 3; round++) {
				engine.handleVehiclePositions(positions);
				engine.handleTripUpdates(updates);
			}

			assertSameData(snapshot("idem-3"), afterOneRound);
		}

		@Test
		void aNewMessageCarryingUnchangedContent_changesNothing() {
			stubStaticSchedule("idem-4");
			engine.handleTripUpdates(withHeaderTimestamp(feedWithTripUpdate(scheduledWithEstimate("idem-4")), 1000));
			engine.handleVehiclePositions(withHeaderTimestamp(feedWithVehicle(position("idem-4", EPOCH_T0)), 1000));
			TripSnapshot afterFirstCycle = snapshot("idem-4");

			// the next cycle: new header timestamps, STM has nothing new for this trip
			engine.handleTripUpdates(withHeaderTimestamp(feedWithTripUpdate(scheduledWithEstimate("idem-4")), 1020));
			engine.handleVehiclePositions(withHeaderTimestamp(feedWithVehicle(position("idem-4", EPOCH_T0)), 1020));

			assertSameData(snapshot("idem-4"), afterFirstCycle);
		}

		@Test
		void aCanceledTrip_ingestedTwice_isTheSameBareState() {
			FeedMessage message = feedWithTripUpdate(
					tripUpdate("idem-5", TripDescriptor.ScheduleRelationship.CANCELED, "10", 0));

			engine.handleTripUpdates(message);
			TripSnapshot afterOnce = snapshot("idem-5");
			engine.handleTripUpdates(message);

			assertSameData(snapshot("idem-5"), afterOnce);
			assertThat(store.getActiveTripCount()).isEqualTo(1);
		}
	}

	// --- broken input: nothing throws, nothing bad is stored, the good entities in the same message still land ---

	@Nested
	class BrokenInput {

		@Test
		void aMessageWithNoEntities_isIngestedWithoutEffect() {
			assertThatCode(() -> {
				engine.handleTripUpdates(emptyFeed());
				engine.handleVehiclePositions(emptyFeed());
			}).doesNotThrowAnyException();

			assertThat(store.getActiveTripCount()).isZero();
		}

		@Test
		void anEntityOfAnotherKind_isIgnored() {
			FeedMessage onlyAnAlert = FeedMessage.newBuilder()
					.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").build())
					.addEntity(FeedEntity.newBuilder().setId("alert-1")
							.setAlert(com.google.transit.realtime.GtfsRealtime.Alert.newBuilder().build()).build())
					.build();

			assertThatCode(() -> {
				engine.handleTripUpdates(onlyAnAlert);
				engine.handleVehiclePositions(onlyAnAlert);
			}).doesNotThrowAnyException();

			assertThat(store.getActiveTripCount()).isZero();
		}

		@Test
		void aTripUpdateWithABlankTripId_isSkipped() {
			engine.handleTripUpdates(feedWithTripUpdate(
					tripUpdate("", TripDescriptor.ScheduleRelationship.SCHEDULED, "10", 0)));

			assertThat(store.getActiveTripCount()).isZero();
		}

		@Test
		void aStopTimeUpdateWithoutAStopSequence_isIgnored_andTheRestOfTheUpdateStillApplies() {
			stubStaticSchedule("broken-1");
			TripUpdate update = TripUpdate.newBuilder()
					.setTrip(TripDescriptor.newBuilder().setTripId("broken-1").setStartDate(SERVICE_DATE)
							.setScheduleRelationship(TripDescriptor.ScheduleRelationship.SCHEDULED).build())
					.addStopTimeUpdate(TripUpdate.StopTimeUpdate.newBuilder()
							.setArrival(TripUpdate.StopTimeEvent.newBuilder().setTime(EPOCH_T0 + 600).build()).build())
					.build();

			engine.handleTripUpdates(feedWithTripUpdate(update));

			ActiveTripState state = store.fetchTripState("broken-1").orElseThrow();
			assertThat(state.getScheduleRelationship()).isEqualTo("SCHEDULED");
			assertThat(state.getStopSchedule()).allSatisfy(stop -> assertThat(stop.estimatedArrival()).isNull());
		}

		@Test
		void aStopTimeUpdateForAStopTheScheduleDoesNotHave_isIgnored() {
			stubStaticSchedule("broken-2");
			TripUpdate update = TripUpdate.newBuilder()
					.setTrip(TripDescriptor.newBuilder().setTripId("broken-2").setStartDate(SERVICE_DATE)
							.setScheduleRelationship(TripDescriptor.ScheduleRelationship.SCHEDULED).build())
					.addStopTimeUpdate(TripUpdate.StopTimeUpdate.newBuilder()
							.setStopSequence(99)
							.setArrival(TripUpdate.StopTimeEvent.newBuilder().setTime(EPOCH_T0 + 600).build()).build())
					.build();

			engine.handleTripUpdates(feedWithTripUpdate(update));

			assertThat(store.fetchTripState("broken-2").orElseThrow().getStopSchedule())
					.allSatisfy(stop -> assertThat(stop.estimatedArrival()).isNull());
		}

		@Test
		void anAddedTrip_hasNoStaticSchedule_andGetsABareStateWithoutDatabaseWork() {
			engine.handleTripUpdates(feedWithTripUpdate(
					tripUpdate("broken-3", TripDescriptor.ScheduleRelationship.ADDED, "10", 0)));

			assertThat(store.fetchTripState("broken-3").orElseThrow().getStopSchedule()).isNull();
			verify(tripRepository, never()).findById("broken-3");
		}

		@Test
		void aVehiclePositionWithoutAPosition_isSkipped() {
			VehiclePosition noPosition = VehiclePosition.newBuilder()
					.setTrip(TripDescriptor.newBuilder().setTripId("broken-4").setStartDate(SERVICE_DATE).build())
					.setTimestamp(EPOCH_T0)
					.build();

			assertThatCode(() -> engine.handleVehiclePositions(feedWithVehicle(noPosition)))
					.doesNotThrowAnyException();

			assertThat(store.getActiveTripCount()).isZero();
		}

		@Test
		void coordinatesThatAreNotRealPlaces_neverBecomePings() {
			float[][] impossible = {
					{Float.NaN, -73.57f}, {45.52f, Float.NaN},
					{Float.POSITIVE_INFINITY, -73.57f}, {45.52f, Float.NEGATIVE_INFINITY},
					{200f, -73.57f}, {45.52f, -500f}};

			for (int i = 0; i < impossible.length; i++) {
				String tripId = "broken-coords-" + i;
				lenient().when(tripRepository.findById(tripId)).thenReturn(Optional.of(buildFakeTrip(tripId, 100, 200)));
				lenient().when(shapeRepository.findByRoutePatternIdOrderByIdShapePtSequenceAsc(any()))
						.thenReturn(buildFakeShapes());

				VehiclePosition vehicle = vehicleAt(tripId, impossible[i][0], impossible[i][1])
						.setTimestamp(EPOCH_T0).build();

				assertThatCode(() -> engine.handleVehiclePositions(feedWithVehicle(vehicle)))
						.doesNotThrowAnyException();

				assertThat(store.fetchTripState(tripId).map(state -> state.getRecentPings().size()).orElse(0))
						.as("pings for %s", tripId).isZero();
			}
		}
	}

	// --- service dates: only a trip update states one; a position joins the trip or gets its day from the schedule ---
	//
	// A late-night trip scheduled at 25:55 belongs to the service day that began the day before. tripUpdates carry that
	// service date; vehiclePositions carry the calendar day the trip started on. They are one trip, not two.

	@Nested
	class ServiceDates {

		private static final String SERVICE_DAY = "20260921";
		private static final String CALENDAR_DAY = "20260922";
		// 25:55:16 and 25:56:40 of the service day: 01:55 and 01:56 on the 22nd, Montreal time
		private static final int FIRST_STOP_SECONDS = 93316;
		private static final int SECOND_STOP_SECONDS = 93400;
		private final long minutePastFirstStop = Instant.parse("2026-09-22T05:56:00Z").getEpochSecond();

		private FeedMessage updateDated(String tripId, String startDate) {
			return feedWithTripUpdate(TripUpdate.newBuilder()
					.setTrip(TripDescriptor.newBuilder().setTripId(tripId).setStartDate(startDate)
							.setScheduleRelationship(TripDescriptor.ScheduleRelationship.SCHEDULED)
							.setRouteId("10").setDirectionId(0).build())
					.addStopTimeUpdate(TripUpdate.StopTimeUpdate.newBuilder()
							.setStopSequence(2)
							.setArrival(TripUpdate.StopTimeEvent.newBuilder().setTime(minutePastFirstStop + 90).build())
							.setScheduleRelationship(TripUpdate.StopTimeUpdate.ScheduleRelationship.SCHEDULED)
							.build())
					.build());
		}

		private FeedMessage positionDated(String tripId, String startDate, long timestamp) {
			return feedWithVehicle(vehicleAt(tripId, 45.525f, -73.575f)
					.setTrip(TripDescriptor.newBuilder().setTripId(tripId).setStartDate(startDate).build())
					.setTimestamp(timestamp)
					.build());
		}

		private TripSnapshot trip(String tripId) {
			return store.snapshotTrip(tripId).orElseThrow();
		}

		@Test
		void aPositionDatedTheNextDay_joinsTheTripAUpdateCreatedForTheDayBefore() {
			stubStaticScheduleWithTimes("late-1", FIRST_STOP_SECONDS, SECOND_STOP_SECONDS);

			engine.handleTripUpdates(updateDated("late-1", SERVICE_DAY));
			engine.handleVehiclePositions(positionDated("late-1", CALENDAR_DAY, minutePastFirstStop));

			assertThat(trip("late-1").serviceDate()).isEqualTo(LocalDate.of(2026, 9, 21));
			assertThat(trip("late-1").recentPings()).hasSize(1);
			assertThat(trip("late-1").stopSchedule().get(1).estimatedArrival())
					.isEqualTo(Instant.ofEpochSecond(minutePastFirstStop + 90));
		}

		@Test
		void feedsThatKeepDisagreeingOnTheDate_leaveOneStableState() {
			stubStaticScheduleWithTimes("late-2", FIRST_STOP_SECONDS, SECOND_STOP_SECONDS);

			for (int cycle = 0; cycle < 3; cycle++) {
				engine.handleTripUpdates(withHeaderTimestamp(updateDated("late-2", SERVICE_DAY), 1000 + cycle * 20L));
				engine.handleVehiclePositions(withHeaderTimestamp(
						positionDated("late-2", CALENDAR_DAY, minutePastFirstStop + cycle * 20L), 1000 + cycle * 20L));
			}

			assertThat(trip("late-2").serviceDate()).isEqualTo(LocalDate.of(2026, 9, 21));
			assertThat(trip("late-2").recentPings()).hasSize(3);
			assertThat(trip("late-2").stopSchedule().get(1).estimatedArrival()).isNotNull();
		}

		@Test
		void aPositionThatCreatesTheTrip_getsTheServiceDayFromTheSchedule() {
			stubStaticScheduleWithTimes("late-3", FIRST_STOP_SECONDS, SECOND_STOP_SECONDS);

			engine.handleVehiclePositions(positionDated("late-3", CALENDAR_DAY, minutePastFirstStop));

			ActiveTripState state = store.fetchTripState("late-3").orElseThrow();
			assertThat(state.getServiceDate()).isEqualTo(LocalDate.of(2026, 9, 21));
			assertThat(state.getStopSchedule().get(0).scheduledArrival()).isEqualTo(Instant.parse("2026-09-22T05:55:16Z"));
			assertThat(state.isServiceDateProvisional()).isTrue();
		}

		@Test
		void theTripUpdateThatFollows_confirmsTheDateWithoutRebuildingTheTrip() {
			stubStaticScheduleWithTimes("late-4", FIRST_STOP_SECONDS, SECOND_STOP_SECONDS);
			engine.handleVehiclePositions(positionDated("late-4", CALENDAR_DAY, minutePastFirstStop));
			ActiveTripState created = store.fetchTripState("late-4").orElseThrow();

			engine.handleTripUpdates(updateDated("late-4", SERVICE_DAY));
			engine.handleVehiclePositions(positionDated("late-4", CALENDAR_DAY, minutePastFirstStop + 20));

			ActiveTripState afterwards = store.fetchTripState("late-4").orElseThrow();
			assertThat(afterwards).isSameAs(created);
			assertThat(afterwards.isServiceDateProvisional()).isFalse();
			assertThat(trip("late-4").recentPings()).hasSize(2);
			assertThat(trip("late-4").stopSchedule().get(1).estimatedArrival()).isNotNull();
		}

		@Test
		void aTripThatStartsBeforeMidnight_keepsTheDateOfThePosition() {
			stubStaticScheduleWithTimes("day-1", 28800, 29000);

			engine.handleVehiclePositions(positionDated("day-1", SERVICE_DAY, EPOCH_T0));

			assertThat(trip("day-1").serviceDate()).isEqualTo(LocalDate.of(2026, 9, 21));
		}

		@Test
		void aPositionNeverReplacesTheTripHeld() {
			stubStaticScheduleWithTimes("late-5", FIRST_STOP_SECONDS, SECOND_STOP_SECONDS);
			engine.handleTripUpdates(updateDated("late-5", SERVICE_DAY));
			ActiveTripState held = store.fetchTripState("late-5").orElseThrow();

			engine.handleVehiclePositions(positionDated("late-5", "20260930", minutePastFirstStop));

			assertThat(store.fetchTripState("late-5").orElseThrow()).isSameAs(held);
			assertThat(held.getServiceDate()).isEqualTo(LocalDate.of(2026, 9, 21));
			assertThat(trip("late-5").recentPings()).hasSize(1);
			assertThat(trip("late-5").stopSchedule().get(1).estimatedArrival()).isNotNull();
		}

		@Test
		void aTripUpdateWithAnotherDate_replacesAGuessedDate_andKeepsThePositionsAlreadyCollected() {
			// a position names a date the schedule rule does not shift, and a trip update then says otherwise
			stubStaticScheduleWithTimes("day-2", 28800, 29000);
			engine.handleVehiclePositions(positionDated("day-2", "20260922", EPOCH_T0));
			assertThat(trip("day-2").serviceDate()).isEqualTo(LocalDate.of(2026, 9, 22));

			engine.handleTripUpdates(updateDated("day-2", SERVICE_DAY));

			assertThat(trip("day-2").serviceDate()).isEqualTo(LocalDate.of(2026, 9, 21));
			assertThat(trip("day-2").recentPings()).hasSize(1);
			assertThat(store.fetchTripState("day-2").orElseThrow().isServiceDateProvisional()).isFalse();
		}

		@Test
		void aTripUpdateWithANewDate_afterTheDateWasStated_startsAFreshRunWithoutTheOldPositions() {
			stubStaticScheduleWithTimes("late-6", FIRST_STOP_SECONDS, SECOND_STOP_SECONDS);
			engine.handleTripUpdates(updateDated("late-6", SERVICE_DAY));
			engine.handleVehiclePositions(positionDated("late-6", CALENDAR_DAY, minutePastFirstStop));
			assertThat(trip("late-6").recentPings()).hasSize(1);

			engine.handleTripUpdates(updateDated("late-6", "20260922"));

			assertThat(trip("late-6").serviceDate()).isEqualTo(LocalDate.of(2026, 9, 22));
			assertThat(trip("late-6").recentPings()).isEmpty();
		}

		@Test
		void aPositionWithNoDate_cannotCreateATrip_butStillJoinsOneThatExists() {
			stubStaticScheduleWithTimes("late-7", FIRST_STOP_SECONDS, SECOND_STOP_SECONDS);
			FeedMessage noDate = feedWithVehicle(vehicleAt("late-7", 45.525f, -73.575f)
					.setTrip(TripDescriptor.newBuilder().setTripId("late-7").build())
					.setTimestamp(minutePastFirstStop).build());

			engine.handleVehiclePositions(noDate);
			assertThat(store.getActiveTripCount()).isZero();

			engine.handleTripUpdates(updateDated("late-7", SERVICE_DAY));
			engine.handleVehiclePositions(noDate);
			assertThat(trip("late-7").recentPings()).hasSize(1);
		}
	}

	private FeedMessage emptyFeed() {
		return FeedMessage.newBuilder()
				.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").build())
				.build();
	}

	private TripUpdate tripUpdate(String tripId, TripDescriptor.ScheduleRelationship relationship, String routeId,
			int directionId) {
		return tripUpdate(tripId, relationship, routeId, directionId, SERVICE_DATE);
	}

	private TripUpdate tripUpdate(String tripId, TripDescriptor.ScheduleRelationship relationship, String routeId,
			int directionId, String startDate) {
		return TripUpdate.newBuilder()
				.setTrip(TripDescriptor.newBuilder()
						.setTripId(tripId).setStartDate(startDate)
						.setScheduleRelationship(relationship)
						.setRouteId(routeId)
						.setDirectionId(directionId)
						.build())
				.build();
	}

	private FeedMessage feedWithTripUpdate(TripUpdate tripUpdate) {
		return FeedMessage.newBuilder()
				.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").build())
				.addEntity(FeedEntity.newBuilder().setId("1").setTripUpdate(tripUpdate).build())
				.build();
	}

	private FeedMessage withHeaderTimestamp(FeedMessage feed, long timestamp) {
		return feed.toBuilder()
				.setHeader(feed.getHeader().toBuilder().setTimestamp(timestamp).build())
				.build();
	}

	private VehiclePosition.Builder vehicleAt(String tripId, float lat, float lon) {
		return VehiclePosition.newBuilder()
				.setTrip(TripDescriptor.newBuilder().setTripId(tripId).setStartDate(SERVICE_DATE).build())
				.setPosition(Position.newBuilder().setLatitude(lat).setLongitude(lon).build());
	}

	private VehiclePosition vehicleWithSpeed(String tripId, float lat, float lon, long timestamp, float speed) {
		return VehiclePosition.newBuilder()
				.setTrip(TripDescriptor.newBuilder().setTripId(tripId).setStartDate(SERVICE_DATE).build())
				.setPosition(Position.newBuilder().setLatitude(lat).setLongitude(lon).setSpeed(speed).build())
				.setTimestamp(timestamp)
				.build();
	}

	private FeedMessage feedWithVehicle(VehiclePosition vehicle) {
		return FeedMessage.newBuilder()
				.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").build())
				.addEntity(FeedEntity.newBuilder().setId("1").setVehicle(vehicle).build())
				.build();
	}

	private void stubStaticSchedule(String tripId) {
		stubStaticScheduleWithTimes(tripId, 100, 200);
	}

	private void stubStaticScheduleWithTimes(String tripId, int stop1Seconds, int stop2Seconds) {
		when(tripRepository.findById(tripId)).thenReturn(Optional.of(buildFakeTrip(tripId, stop1Seconds, stop2Seconds)));
		when(shapeRepository.findByRoutePatternIdOrderByIdShapePtSequenceAsc(any())).thenReturn(buildFakeShapes());
	}

	private Trip buildFakeTrip(String tripId, int stop1Seconds, int stop2Seconds) {

		Stop stop1 = Stop.builder().stopId("stop-1")
				.stopLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(-73.57, 45.52))).build();
		Stop stop2 = Stop.builder().stopId("stop-2")
				.stopLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(-73.58, 45.53))).build();

		StopTime stopTime1 = StopTime.builder()
				.id(new StopTimeId(tripId, 1))
				.stopId("stop-1")
				.arrivalTime(stop1Seconds)
				.departureTime(stop1Seconds)
				.stop(stop1)
				.build();

		StopTime stopTime2 = StopTime.builder()
				.id(new StopTimeId(tripId, 2))
				.stopId("stop-2")
				.arrivalTime(stop2Seconds)
				.departureTime(stop2Seconds)
				.stop(stop2)
				.build();

		return Trip.builder()
				.tripId(tripId)
				.routeId("10")
				.directionId(0)
				.shapeId("shape-1")
				.routePatternId("pattern-1")
				.stopTimes(List.of(stopTime1, stopTime2))
				.build();
	}

	private List<Shape> buildFakeShapes() {
		return List.of(
				Shape.builder().id(new ShapeId("shape-1", 1))
						.shapeLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(-73.57, 45.52))).build(),
				Shape.builder().id(new ShapeId("shape-1", 2))
						.shapeLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(-73.58, 45.53))).build());
	}
}
