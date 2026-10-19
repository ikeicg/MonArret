package com.monarret.backend.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.monarret.backend.runtime.model.GpsPing;

class ActiveTripStateTest {

	private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

	private final ActiveTripState state = new ActiveTripState();

	@Test
	void pingsInsideTheWindow_areAllKeptInOrder() {
		assertThat(state.addGpsPing(0, 0, T0, null, null)).isTrue();
		assertThat(state.addGpsPing(0, 0, T0.plusSeconds(20), null, null)).isTrue();
		assertThat(state.addGpsPing(0, 0, T0.plusSeconds(40), null, null)).isTrue();

		assertThat(state.getRecentPings()).extracting(GpsPing::timestamp)
				.containsExactly(T0, T0.plusSeconds(20), T0.plusSeconds(40));
	}

	@Test
	void pingsOlderThanTheWindow_areDroppedRelativeToTheNewestPing() {
		for (int s = 0; s <= 140; s += 20) {
			state.addGpsPing(0, 0, T0.plusSeconds(s), null, null);
		}

		// newest is +140 s, so anything before +20 s is out
		assertThat(state.getRecentPings()).extracting(GpsPing::timestamp)
				.containsExactly(T0.plusSeconds(20), T0.plusSeconds(40), T0.plusSeconds(60), T0.plusSeconds(80),
						T0.plusSeconds(100), T0.plusSeconds(120), T0.plusSeconds(140));
	}

	@Test
	void pingExactlyWindowLengthOlderThanNewest_isKept() {
		state.addGpsPing(0, 0, T0, null, null);
		state.addGpsPing(0, 0, T0.plus(ActiveTripState.PING_WINDOW), null, null);

		assertThat(state.getRecentPings()).hasSize(2);
	}

	@Test
	void repeatedTimestamp_isRejected_andNotStoredTwice() {
		assertThat(state.addGpsPing(1, 1, T0, null, null)).isTrue();
		assertThat(state.addGpsPing(2, 2, T0, null, null)).isFalse();

		assertThat(state.getRecentPings()).hasSize(1);
		assertThat(state.getLatestPing().x()).isEqualTo(1);
	}

	@Test
	void outOfOrderPing_isRejected() {
		state.addGpsPing(1, 1, T0.plusSeconds(20), null, null);

		assertThat(state.addGpsPing(2, 2, T0, null, null)).isFalse();
		assertThat(state.getRecentPings()).hasSize(1);
		assertThat(state.getLatestPing().timestamp()).isEqualTo(T0.plusSeconds(20));
	}

	@Test
	void alongRoute_isStoredWithThePing() {
		state.addGpsPing(0, 0, T0, 123.4, 7.5);

		assertThat(state.getLatestPing().alongRoute()).isEqualTo(123.4);
		assertThat(state.getLatestPing().offRouteMeters()).isEqualTo(7.5);
	}

	@Test
	void metersMoved_usesAlongRouteWhenAvailable_otherwiseStraightLine() {
		state.addGpsPing(0, 0, T0, 100.0, 0.0);
		assertThat(state.metersMovedInWindow()).isNull();

		state.addGpsPing(30, 40, T0.plusSeconds(20), 160.0, 0.0);
		assertThat(state.metersMovedInWindow()).isEqualTo(60.0);

		ActiveTripState noRoute = new ActiveTripState();
		noRoute.addGpsPing(0, 0, T0, null, null);
		noRoute.addGpsPing(30, 40, T0.plusSeconds(20), null, null);
		assertThat(noRoute.metersMovedInWindow()).isEqualTo(50.0);
	}

	@Test
	void frozen_needsMovingSpeedAndNoMovement_andAtLeastTwoPings() {
		state.addGpsPing(0, 0, T0, 100.0, 0.0);
		state.updatePositionFrozen(8f);
		assertThat(state.isPositionFrozen()).isFalse();

		state.addGpsPing(0, 0, T0.plusSeconds(20), 100.5, 0.0);
		state.updatePositionFrozen(8f);
		assertThat(state.isPositionFrozen()).isTrue();

		state.updatePositionFrozen(0f);
		assertThat(state.isPositionFrozen()).isFalse();

		state.updatePositionFrozen(null);
		assertThat(state.isPositionFrozen()).isFalse();
	}

	@Test
	void twoStatesWithTheSameFields_areStillDifferentObjects() {
		ActiveTripState a = new ActiveTripState();
		ActiveTripState b = new ActiveTripState();
		a.setTripId("t");
		b.setTripId("t");

		assertThat(a).isNotEqualTo(b);
		assertThat(a).isEqualTo(a);
	}

	// --- where the bus has been sitting ---

	@Test
	void aBusSittingStill_keepsItsStationaryClockAtTheFirstPing_evenAfterThePingWindowMovesOn() {
		for (int s = 0; s <= 300; s += 20) {
			state.addGpsPing(100, 200, T0.plusSeconds(s), null, null);
		}

		assertThat(state.toSnapshot().stationarySince()).isEqualTo(T0);
		assertThat(state.getRecentPings().size()).isLessThan(16);
	}

	@Test
	void gpsWobbleInsideTheRadius_doesNotRestartTheClock() {
		state.addGpsPing(100, 200, T0, null, null);
		state.addGpsPing(108, 205, T0.plusSeconds(20), null, null);
		state.addGpsPing(95, 193, T0.plusSeconds(40), null, null);

		assertThat(state.toSnapshot().stationarySince()).isEqualTo(T0);
	}

	@Test
	void movingBeyondTheRadius_restartsTheClockAtTheNewSpot() {
		state.addGpsPing(0, 0, T0, null, null);
		state.addGpsPing(0, 0, T0.plusSeconds(60), null, null);
		state.addGpsPing(100, 0, T0.plusSeconds(80), null, null);

		assertThat(state.toSnapshot().stationarySince()).isEqualTo(T0.plusSeconds(80));
	}

	@Test
	void aBusCreepingInSmallSteps_isMeasuredFromWhereItFirstStopped_notFromEachNeighbouringPing() {
		// 6 m every 20 s never exceeds the radius from the first ping for three pings, then it does
		state.addGpsPing(0, 0, T0, null, null);
		state.addGpsPing(6, 0, T0.plusSeconds(20), null, null);
		state.addGpsPing(12, 0, T0.plusSeconds(40), null, null);
		state.addGpsPing(18, 0, T0.plusSeconds(60), null, null);

		assertThat(state.toSnapshot().stationarySince()).isEqualTo(T0.plusSeconds(60));
	}

	@Test
	void noPingYet_hasNoStationaryClock() {
		assertThat(state.toSnapshot().stationarySince()).isNull();
	}

	@Test
	void takingOverPositionsFromAnotherState_carriesTheStationaryClock() {
		ActiveTripState old = new ActiveTripState();
		old.addGpsPing(5, 5, T0, null, null);
		old.addGpsPing(5, 5, T0.plusSeconds(200), null, null);

		state.takeOverPositionsFrom(old);

		assertThat(state.toSnapshot().stationarySince()).isEqualTo(T0);
	}
}
