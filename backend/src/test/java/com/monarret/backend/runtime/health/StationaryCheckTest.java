package com.monarret.backend.runtime.health;

import static com.monarret.backend.runtime.health.TripFixtures.T0;
import static com.monarret.backend.runtime.health.TripFixtures.ping;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.monarret.backend.runtime.model.GpsPing;
import com.monarret.backend.runtime.model.TripSnapshot;

class StationaryCheckTest {

	private static TripSnapshot trip(List<GpsPing> pings, Instant stationarySince) {
		return new TripSnapshot("t1", "10", 0, "shape", LocalDate.of(2026, 10, 3), 4000.0, List.of(), pings, T0, null,
				null, null, null, "SCHEDULED", false, stationarySince);
	}

	@Test
	void sittingFiveMinutes_reportsThreeHundredSeconds() {
		TripSnapshot trip = trip(List.of(ping(T0.plusSeconds(300), 1000.0)), T0);

		assertThat(StationaryCheck.seconds(trip, T0.plusSeconds(310))).isEqualTo(300);
	}

	@Test
	void exactlyTwoMinutes_isShown_andAnythingShorterIsNot() {
		assertThat(StationaryCheck.seconds(trip(List.of(ping(T0.plusSeconds(120), 1000.0)), T0), T0.plusSeconds(125)))
				.isEqualTo(120);
		assertThat(StationaryCheck.seconds(trip(List.of(ping(T0.plusSeconds(119), 1000.0)), T0), T0.plusSeconds(125)))
				.isNull();
	}

	@Test
	void aPositionTooOldToSayWhereTheBusIsNow_reportsNothing() {
		TripSnapshot trip = trip(List.of(ping(T0.plusSeconds(300), 1000.0)), T0);

		assertThat(StationaryCheck.seconds(trip, T0.plusSeconds(300 + 121))).isNull();
		assertThat(StationaryCheck.seconds(trip, T0.plusSeconds(300 + 120))).isEqualTo(300);
	}

	@Test
	void noPositionOrNoClock_reportsNothing() {
		assertThat(StationaryCheck.seconds(trip(List.of(), null), T0)).isNull();
		assertThat(StationaryCheck.seconds(trip(List.of(ping(T0, 1000.0)), null), T0)).isNull();
	}
}
