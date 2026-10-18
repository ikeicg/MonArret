package com.monarret.backend.runtime.health;

import static com.monarret.backend.runtime.health.TripFixtures.T0;
import static com.monarret.backend.runtime.health.TripFixtures.fiveStops;
import static com.monarret.backend.runtime.health.TripFixtures.fiveStopsReplacing;
import static com.monarret.backend.runtime.health.TripFixtures.ping;
import static com.monarret.backend.runtime.health.TripFixtures.stopWithEstimate;
import static com.monarret.backend.runtime.health.TripFixtures.trip;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.monarret.backend.runtime.model.StopTimeContext;
import com.monarret.backend.runtime.model.TripSnapshot;

// The rider's stop is stop 3, scheduled at T0 + 20 minutes.
class TimingCheckTest {

	private static final int RIDER_STOP = 3;
	private static final Instant SCHEDULED = T0.plusSeconds(1200);
	private static final Instant NOW = T0.plusSeconds(1200 - 3600);

	private TripSnapshot live(List<StopTimeContext> stops) {
		return trip(stops, List.of(ping(NOW, 100.0)), 1, "IN_TRANSIT_TO", null, "SCHEDULED");
	}

	private StopTiming timingFor(Instant predicted) {
		return TimingCheck.evaluate(live(fiveStopsReplacing(stopWithEstimate(RIDER_STOP, predicted))), RIDER_STOP, NOW);
	}

	@Test
	void withoutAVehicle_isNull() {
		assertThat(TimingCheck.evaluate(trip(fiveStops(), List.of(), null, null, null, "SCHEDULED"), RIDER_STOP, NOW))
				.isNull();
	}

	@Test
	void stopNotInTheSchedule_isNull() {
		assertThat(TimingCheck.evaluate(live(fiveStops()), 99, NOW)).isNull();
	}

	@Test
	void predictionEqualToTheSchedule_isNormal() {
		StopTiming result = timingFor(SCHEDULED);

		assertThat(result.status()).isEqualTo(TimingStatus.NORMAL);
		assertThat(result.scheduled()).isEqualTo(SCHEDULED);
		assertThat(result.predicted()).isEqualTo(SCHEDULED);
		assertThat(result.differenceSeconds()).isZero();
	}

	@Test
	void twoMinutesEarly_isEarly() {
		StopTiming result = timingFor(SCHEDULED.minusSeconds(120));

		assertThat(result.status()).isEqualTo(TimingStatus.EARLY);
		assertThat(result.differenceSeconds()).isEqualTo(-120);
	}

	@Test
	void justUnderTwoMinutesEarly_isNormal() {
		assertThat(timingFor(SCHEDULED.minusSeconds(119)).status()).isEqualTo(TimingStatus.NORMAL);
	}

	@Test
	void fiveMinutesLate_isLate() {
		StopTiming result = timingFor(SCHEDULED.plusSeconds(300));

		assertThat(result.status()).isEqualTo(TimingStatus.LATE);
		assertThat(result.differenceSeconds()).isEqualTo(300);
	}

	@Test
	void justUnderFiveMinutesLate_isNormal() {
		assertThat(timingFor(SCHEDULED.plusSeconds(299)).status()).isEqualTo(TimingStatus.NORMAL);
	}

	@Test
	void skippedStopWithNoTimes_hasNoPrediction() {
		StopTimeContext skipped = new StopTimeContext("stop-3", "Stop 3", 3, SCHEDULED, null, SCHEDULED, null, 0, 0,
				2000.0, "SKIPPED");

		StopTiming result = TimingCheck.evaluate(live(fiveStopsReplacing(skipped)), RIDER_STOP, NOW);

		assertThat(result.status()).isEqualTo(TimingStatus.NO_PREDICTION);
		assertThat(result.predicted()).isNull();
		assertThat(result.scheduled()).isEqualTo(SCHEDULED);
	}

	@Test
	void stopTheFeedNeverGaveATimeFor_hasNoPrediction() {
		StopTimeContext unstated = new StopTimeContext("stop-3", "Stop 3", 3, SCHEDULED, null, SCHEDULED, null, 0, 0,
				2000.0, null);

		StopTiming result = TimingCheck.evaluate(live(fiveStopsReplacing(unstated)), RIDER_STOP, NOW);

		assertThat(result.status()).isEqualTo(TimingStatus.NO_PREDICTION);
		assertThat(result.predicted()).isNull();
	}

	@Test
	void missingDepartureTimes_fallBackToArrival() {
		Instant arrival = SCHEDULED.plusSeconds(400);
		StopTimeContext arrivalOnly = new StopTimeContext("stop-3", "Stop 3", 3, SCHEDULED, arrival, null, null, 0, 0,
				2000.0, "SCHEDULED");

		StopTiming result = TimingCheck.evaluate(live(fiveStopsReplacing(arrivalOnly)), RIDER_STOP, NOW);

		assertThat(result.scheduled()).isEqualTo(SCHEDULED);
		assertThat(result.predicted()).isEqualTo(arrival);
		assertThat(result.status()).isEqualTo(TimingStatus.LATE);
	}

	@Test
	void predictionMoreThanFiveMinutesAhead_isAnEstimate() {
		assertThat(timingFor(NOW.plusSeconds(301)).estimate()).isTrue();
		assertThat(timingFor(NOW.plusSeconds(300)).estimate()).isFalse();
	}
}
