package com.catchmystm.backend.runtime.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.catchmystm.backend.runtime.model.GpsPing;
import com.catchmystm.backend.runtime.model.TripSnapshot;

class TripChecksTest {

	private static final Instant T0 = Instant.parse("2026-10-03T12:00:00Z");

	// --- RouteStatusCheck ---

	@Test
	void route_fewerThanThreePings_isUnknown() {
		assertThat(RouteStatusCheck.evaluate(snapshot(offRoute(100.0, 100.0)))).isEqualTo(RouteStatus.UNKNOWN);
	}

	@Test
	void route_noPings_isUnknown() {
		assertThat(RouteStatusCheck.evaluate(snapshot())).isEqualTo(RouteStatus.UNKNOWN);
	}

	@Test
	void route_pingWithoutRouteGeometry_isUnknown() {
		assertThat(RouteStatusCheck.evaluate(snapshot(offRoute(100.0, null, 100.0)))).isEqualTo(RouteStatus.UNKNOWN);
	}

	@Test
	void route_allPingsBeyondTheLimit_isNotOnRoute() {
		assertThat(RouteStatusCheck.evaluate(snapshot(offRoute(60.0, 80.0, 51.0))))
				.isEqualTo(RouteStatus.NOT_ON_ROUTE);
	}

	@Test
	void route_onePingWithinTheLimit_isOnRoute() {
		assertThat(RouteStatusCheck.evaluate(snapshot(offRoute(60.0, 10.0, 80.0)))).isEqualTo(RouteStatus.ON_ROUTE);
	}

	@Test
	void route_pingExactlyAtTheLimit_countsAsOnRoute() {
		assertThat(RouteStatusCheck.evaluate(snapshot(offRoute(60.0, 50.0, 80.0)))).isEqualTo(RouteStatus.ON_ROUTE);
	}

	@Test
	void route_rejoiningTheRoute_clearsTheStatus() {
		assertThat(RouteStatusCheck.evaluate(snapshot(offRoute(100.0, 100.0, 100.0, 5.0))))
				.isEqualTo(RouteStatus.ON_ROUTE);
	}

	// --- MovementCheck ---

	@Test
	void movement_fewerThanTwoPings_isUnknown() {
		assertThat(MovementCheck.evaluate(snapshot(at(0, 0)))).isEqualTo(MovementStatus.UNKNOWN);
	}

	@Test
	void movement_movedInTheWindow_isMoving() {
		assertThat(MovementCheck.evaluate(snapshot(at(0, 0), at(20, 150)))).isEqualTo(MovementStatus.MOVING);
	}

	@Test
	void movement_notMovedForTheWholeWindow_isStationary() {
		assertThat(MovementCheck.evaluate(snapshot(at(0, 100), at(40, 100), at(80, 100), at(120, 100.5))))
				.isEqualTo(MovementStatus.STATIONARY);
	}

	@Test
	void movement_notMovedButWindowTooShort_isUnknown() {
		assertThat(MovementCheck.evaluate(snapshot(at(0, 100), at(20, 100), at(40, 100))))
				.isEqualTo(MovementStatus.UNKNOWN);
	}

	@Test
	void movement_oneMeterBelowTheThreshold_isStationary_oneAboveIsMoving() {
		assertThat(MovementCheck.evaluate(snapshot(at(0, 100), at(120, 101.9)))).isEqualTo(MovementStatus.STATIONARY);
		assertThat(MovementCheck.evaluate(snapshot(at(0, 100), at(120, 102.0)))).isEqualTo(MovementStatus.MOVING);
	}

	@Test
	void movement_withoutRouteDistances_usesStraightLine() {
		GpsPing first = new GpsPing(0, 0, T0, null, null);
		GpsPing last = new GpsPing(3, 4, T0.plusSeconds(20), null, null);

		assertThat(MovementCheck.evaluate(snapshot(first, last))).isEqualTo(MovementStatus.MOVING);
	}

	// --- helpers ---

	private GpsPing[] offRoute(Double... distances) {
		GpsPing[] pings = new GpsPing[distances.length];
		for (int i = 0; i < distances.length; i++) {
			pings[i] = new GpsPing(0, 0, T0.plusSeconds(20L * i), 0.0, distances[i]);
		}
		return pings;
	}

	private GpsPing at(int seconds, double alongRoute) {
		return new GpsPing(0, 0, T0.plusSeconds(seconds), alongRoute, 0.0);
	}

	private TripSnapshot snapshot(GpsPing... pings) {
		return new TripSnapshot("t1", "10", 0, "shape", LocalDate.of(2026, 10, 3), 1000.0, List.of(),
				List.of(pings), T0, null, null, null, null, "SCHEDULED", false);
	}
}
