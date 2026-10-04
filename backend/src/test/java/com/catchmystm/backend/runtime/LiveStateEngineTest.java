package com.catchmystm.backend.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.Mock;
import org.locationtech.proj4j.ProjCoordinate;
import org.mockito.junit.jupiter.MockitoExtension;

import com.catchmystm.backend.entity.Shape;
import com.catchmystm.backend.entity.ShapeId;
import com.catchmystm.backend.entity.Stop;
import com.catchmystm.backend.entity.StopTime;
import com.catchmystm.backend.entity.StopTimeId;
import com.catchmystm.backend.entity.Trip;
import com.catchmystm.backend.repository.ShapeRepository;
import com.catchmystm.backend.repository.TripRepository;
import com.catchmystm.backend.runtime.model.StopTimeContext;
import com.catchmystm.backend.service.StmPoller;
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

		engine = new LiveStateEngine(poller, store, geoOpsService, fixedClock);
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
