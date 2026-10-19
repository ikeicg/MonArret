package com.monarret.backend.runtime.health;

import static com.monarret.backend.runtime.health.TripFixtures.ping;
import static com.monarret.backend.runtime.health.TripFixtures.fiveStops;
import static com.monarret.backend.runtime.health.TripFixtures.ROUTE_LENGTH;
import static com.monarret.backend.runtime.health.TripFixtures.T0;
import static com.monarret.backend.runtime.health.TripFixtures.fiveStopsWith;
import static com.monarret.backend.runtime.health.TripFixtures.pingOffRoute;
import static com.monarret.backend.runtime.health.TripFixtures.trip;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.monarret.backend.runtime.model.GpsPing;
import com.monarret.backend.runtime.model.TripSnapshot;
import com.monarret.backend.service.FeedHealth;

// The rider's stop is stop 4.
class TripHealthAssemblerTest {

	private static final int RIDER_STOP = 4;
	private static final Instant NOW = T0.plusSeconds(100);

	private TripSnapshot liveTrip(String tripRelationship, String stopFourRelationship) {
		List<GpsPing> pings = List.of(
				pingOffRoute(NOW.minusSeconds(60), 1000.0, 0.0),
				pingOffRoute(NOW.minusSeconds(40), 1200.0, 0.0),
				pingOffRoute(NOW.minusSeconds(20), 1500.0, 0.0));
		return trip(fiveStopsWith(RIDER_STOP, stopFourRelationship), pings, 2, "IN_TRANSIT_TO",
				"FEW_SEATS_AVAILABLE", tripRelationship);
	}

	@Test
	void liveTrip_fillsEveryItem() {
		TripHealth health = TripHealthAssembler.assemble(liveTrip("SCHEDULED", "SCHEDULED"), RIDER_STOP, NOW,
				FeedHealth.HEALTHY);

		assertThat(health.tripId()).isEqualTo("t1");
		assertThat(health.routeId()).isEqualTo("10");
		assertThat(health.directionId()).isZero();
		assertThat(health.stopSequence()).isEqualTo(RIDER_STOP);
		assertThat(health.updatedAt()).isEqualTo(T0);
		assertThat(health.headline().status()).isEqualTo(HeadlineStatus.LIVE);
		assertThat(health.stopStatus()).isEqualTo(StopStatus.SCHEDULED);
		assertThat(health.progress().state()).isEqualTo(ProgressState.BEFORE_STOP);
		assertThat(health.progress().stopsAway()).isEqualTo(3);
		assertThat(health.progress().movement()).isEqualTo(MovementStatus.MOVING);
		assertThat(health.timing().status()).isEqualTo(TimingStatus.NORMAL);
		assertThat(health.crowding()).isEqualTo(Occupancy.FEW_SEATS_AVAILABLE);
		assertThat(health.routeStatus()).isEqualTo(RouteStatus.ON_ROUTE);
	}

	@Test
	void feedDown_returnsTheHeadlineOnly() {
		TripHealth health = TripHealthAssembler.assemble(liveTrip("SCHEDULED", "SCHEDULED"), RIDER_STOP, NOW,
				FeedHealth.DOWN);

		assertThat(health.headline().status()).isEqualTo(HeadlineStatus.LIVE_DATA_UNAVAILABLE);
		assertOnlyHeadline(health);
	}

	@Test
	void cancelledTrip_returnsTheHeadlineOnly() {
		TripHealth health = TripHealthAssembler.assemble(liveTrip("CANCELED", "SCHEDULED"), RIDER_STOP, NOW,
				FeedHealth.HEALTHY);

		assertThat(health.headline().status()).isEqualTo(HeadlineStatus.CANCELLED);
		assertOnlyHeadline(health);
	}

	@Test
	void skippedRiderStop_isReported() {
		TripHealth health = TripHealthAssembler.assemble(liveTrip("SCHEDULED", "SKIPPED"), RIDER_STOP, NOW,
				FeedHealth.HEALTHY);

		assertThat(health.stopStatus()).isEqualTo(StopStatus.SKIPPED);
	}

	@Test
	void noVehicle_hasNoProgressTimingOrCrowding_butKeepsTheStopStatus() {
		TripSnapshot noVehicle = trip(fiveStopsWith(RIDER_STOP, "SKIPPED"), List.of(), null, null, null, "SCHEDULED");

		TripHealth health = TripHealthAssembler.assemble(noVehicle, RIDER_STOP, T0.minusSeconds(300), FeedHealth.HEALTHY);

		assertThat(health.headline().status()).isEqualTo(HeadlineStatus.NOT_TRACKED_YET);
		assertThat(health.stopStatus()).isEqualTo(StopStatus.SKIPPED);
		assertThat(health.progress()).isNull();
		assertThat(health.timing()).isNull();
		assertThat(health.crowding()).isNull();
		assertThat(health.routeStatus()).isEqualTo(RouteStatus.UNKNOWN);
	}

	@Test
	void passedStop_hasNoTiming() {
		List<GpsPing> pings = List.of(
				pingOffRoute(NOW.minusSeconds(40), 3400.0, 0.0),
				pingOffRoute(NOW.minusSeconds(20), 3500.0, 0.0));
		TripSnapshot passed = trip(fiveStopsWith(0, null), pings, 5, "IN_TRANSIT_TO", null, "SCHEDULED");

		TripHealth health = TripHealthAssembler.assemble(passed, RIDER_STOP, NOW, FeedHealth.HEALTHY);

		assertThat(health.progress().state()).isEqualTo(ProgressState.PASSED);
		assertThat(health.timing()).isNull();
	}

	@Test
	void tripOffItsRoute_isReported() {
		List<GpsPing> pings = List.of(
				pingOffRoute(NOW.minusSeconds(60), 1000.0, 120.0),
				pingOffRoute(NOW.minusSeconds(40), 1200.0, 150.0),
				pingOffRoute(NOW.minusSeconds(20), 1500.0, 90.0));
		TripSnapshot off = trip(fiveStopsWith(0, null), pings, 2, "IN_TRANSIT_TO", null, "SCHEDULED");

		assertThat(TripHealthAssembler.assemble(off, RIDER_STOP, NOW, FeedHealth.HEALTHY).routeStatus())
				.isEqualTo(RouteStatus.NOT_ON_ROUTE);
	}

	@Test
	void tripWithoutState_andAHealthyFeed_hasNoLiveInfo() {
		TripHealth health = TripHealthAssembler.assembleWithoutState("t9", RIDER_STOP, FeedHealth.HEALTHY);

		assertThat(health.tripId()).isEqualTo("t9");
		assertThat(health.headline().status()).isEqualTo(HeadlineStatus.NO_LIVE_INFO);
		assertOnlyHeadline(health);
	}

	@Test
	void tripWithoutState_andAFeedThatIsNotHealthy_isLiveDataUnavailable() {
		assertThat(TripHealthAssembler.assembleWithoutState("t9", RIDER_STOP, FeedHealth.DOWN).headline().status())
				.isEqualTo(HeadlineStatus.LIVE_DATA_UNAVAILABLE);
		assertThat(TripHealthAssembler.assembleWithoutState("t9", RIDER_STOP, FeedHealth.STARTING).headline().status())
				.isEqualTo(HeadlineStatus.LIVE_DATA_UNAVAILABLE);
	}

	private void assertOnlyHeadline(TripHealth health) {
		assertThat(health.stopStatus()).isNull();
		assertThat(health.progress()).isNull();
		assertThat(health.timing()).isNull();
		assertThat(health.crowding()).isNull();
		assertThat(health.routeStatus()).isNull();
	}

	// --- time spent sitting still ---

	@Test
	void aBusSittingForMinutes_carriesTheTimeAndReadsAsStationary() {
		Instant now = T0.plusSeconds(320);
		TripSnapshot trip = new TripSnapshot("t1", "10", 0, "shape", java.time.LocalDate.of(2026, 10, 3), ROUTE_LENGTH,
				fiveStops(), java.util.List.of(ping(T0.plusSeconds(310), 1500.0)), T0, 2, "stop-2", "STOPPED_AT", null,
				"SCHEDULED", false, T0);

		TripHealth health = TripHealthAssembler.assemble(trip, 4, now, FeedHealth.HEALTHY);

		assertThat(health.progress().stationarySeconds()).isEqualTo(310);
		assertThat(health.progress().movement()).isEqualTo(MovementStatus.STATIONARY);
	}

	@Test
	void aBusThatJustStopped_carriesNoTime() {
		Instant now = T0.plusSeconds(60);
		TripSnapshot trip = new TripSnapshot("t1", "10", 0, "shape", java.time.LocalDate.of(2026, 10, 3), ROUTE_LENGTH,
				fiveStops(), java.util.List.of(ping(T0.plusSeconds(50), 1500.0)), T0, 2, "stop-2", "IN_TRANSIT_TO", null,
				"SCHEDULED", false, T0);

		TripHealth health = TripHealthAssembler.assemble(trip, 4, now, FeedHealth.HEALTHY);

		assertThat(health.progress().stationarySeconds()).isNull();
	}
}
