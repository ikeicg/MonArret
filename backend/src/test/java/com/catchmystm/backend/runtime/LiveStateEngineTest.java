package com.catchmystm.backend.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.Mock;
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

	private ActiveTripStateStore store;
	private Clock fixedClock;
	private LiveStateEngine engine;

	@BeforeEach
	void setUp() {
		GeometricOperationsService geoOpsService = new GeometricOperationsService();
		ActiveTripStateFactory factory = new ActiveTripStateFactory(tripRepository, shapeRepository, geoOpsService);
		store = new ActiveTripStateStore(factory);
		fixedClock = Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneId.of("America/Montreal"));

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
						.setTripId("trip-5")
						.setScheduleRelationship(TripDescriptor.ScheduleRelationship.SCHEDULED)
						.build())
				.addStopTimeUpdate(TripUpdate.StopTimeUpdate.newBuilder()
						.setStopSequence(2)
						.setArrival(com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
								.setTime(arrivalEpoch)
								.build())
						.setScheduleRelationship(TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED)
						.build())
				.build();

		engine.handleTripUpdates(feedWithTripUpdate(update));

		List<StopTimeContext> schedule = store.fetchTripState("trip-5").orElseThrow().getStopSchedule();

		StopTimeContext stop2 = schedule.stream().filter(s -> s.stopSequence() == 2).findFirst().orElseThrow();
		assertThat(stop2.scheduleRelationship()).isEqualTo("SKIPPED");
		assertThat(stop2.estimatedArrival())
				.isEqualTo(LocalTime.ofInstant(Instant.ofEpochSecond(arrivalEpoch), fixedClock.getZone()));

		StopTimeContext stop1 = schedule.stream().filter(s -> s.stopSequence() == 1).findFirst().orElseThrow();
		assertThat(stop1.scheduleRelationship()).isEqualTo("SCHEDULED");
		assertThat(stop1.estimatedArrival()).isEqualTo(stop1.scheduledArrival());
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
				.setTrip(TripDescriptor.newBuilder().setTripId("trip-6").build())
				.setPosition(Position.newBuilder().setLatitude(45.5f).setLongitude(-73.6f).setSpeed(10f).build())
				.setTimestamp(feedEpoch)
				.build();

		FeedMessage feed = FeedMessage.newBuilder()
				.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").build())
				.addEntity(FeedEntity.newBuilder().setId("1").setVehicle(vehicle).build())
				.build();

		engine.handleVehiclePositions(feed);

		ActiveTripState state = store.fetchTripState("trip-6").orElseThrow();
		assertThat(state.getLastGpsPingTime()).isEqualTo(Instant.ofEpochSecond(feedEpoch));
		assertThat(state.getLatestPing().timestamp()).isEqualTo(Instant.ofEpochSecond(feedEpoch));
		assertThat(state.getCurrentSpeedMps()).isEqualTo(10f);
	}

	// --- helpers ---

	private TripUpdate tripUpdate(String tripId, TripDescriptor.ScheduleRelationship relationship, String routeId,
			int directionId) {
		return TripUpdate.newBuilder()
				.setTrip(TripDescriptor.newBuilder()
						.setTripId(tripId)
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

	private void stubStaticSchedule(String tripId) {
		when(tripRepository.findById(tripId)).thenReturn(Optional.of(buildFakeTrip(tripId)));
		when(shapeRepository.findByRoutePatternIdOrderByIdShapePtSequenceAsc(any())).thenReturn(buildFakeShapes());
	}

	private Trip buildFakeTrip(String tripId) {

		Stop stop1 = Stop.builder().stopId("stop-1")
				.stopLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(-73.57, 45.52))).build();
		Stop stop2 = Stop.builder().stopId("stop-2")
				.stopLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(-73.58, 45.53))).build();

		StopTime stopTime1 = StopTime.builder()
				.id(new StopTimeId(tripId, 1))
				.stopId("stop-1")
				.arrivalTime(100)
				.departureTime(100)
				.stop(stop1)
				.build();

		StopTime stopTime2 = StopTime.builder()
				.id(new StopTimeId(tripId, 2))
				.stopId("stop-2")
				.arrivalTime(200)
				.departureTime(200)
				.stop(stop2)
				.build();

		return Trip.builder()
				.tripId(tripId)
				.routeId("10")
				.directionId(0)
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
