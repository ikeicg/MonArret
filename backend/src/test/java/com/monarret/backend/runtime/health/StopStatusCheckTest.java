package com.monarret.backend.runtime.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.monarret.backend.runtime.model.StopTimeContext;
import com.monarret.backend.runtime.model.TripSnapshot;

class StopStatusCheckTest {

	private static final Instant T0 = Instant.parse("2026-10-03T12:00:00Z");

	@Test
	void statedSkipped_isSkipped() {
		assertThat(StopStatusCheck.evaluate(trip(stop(1, "SCHEDULED"), stop(2, "SKIPPED")), 2))
				.isEqualTo(StopStatus.SKIPPED);
	}

	@Test
	void statedNoData_isNoLiveInfo() {
		assertThat(StopStatusCheck.evaluate(trip(stop(1, "NO_DATA")), 1)).isEqualTo(StopStatus.NO_LIVE_INFO);
	}

	@Test
	void statedScheduled_isScheduled() {
		assertThat(StopStatusCheck.evaluate(trip(stop(1, "SCHEDULED")), 1)).isEqualTo(StopStatus.SCHEDULED);
	}

	@Test
	void neverStated_isUnknown_notScheduled() {
		assertThat(StopStatusCheck.evaluate(trip(stop(1, null)), 1)).isEqualTo(StopStatus.UNKNOWN);
	}

	@Test
	void unscheduled_isUnknown() {
		assertThat(StopStatusCheck.evaluate(trip(stop(1, "UNSCHEDULED")), 1)).isEqualTo(StopStatus.UNKNOWN);
	}

	@Test
	void sequenceNotInTheSchedule_isUnknown() {
		assertThat(StopStatusCheck.evaluate(trip(stop(1, "SKIPPED")), 43)).isEqualTo(StopStatus.UNKNOWN);
	}

	@Test
	void emptySchedule_isUnknown() {
		assertThat(StopStatusCheck.evaluate(trip(), 1)).isEqualTo(StopStatus.UNKNOWN);
	}

	@Test
	void otherSkippedStops_areIgnored() {
		assertThat(StopStatusCheck.evaluate(trip(stop(1, "SKIPPED"), stop(2, "SCHEDULED"), stop(3, "SKIPPED")), 2))
				.isEqualTo(StopStatus.SCHEDULED);
	}

	@Test
	void sameStopIdVisitedTwice_isMatchedBySequence() {
		TripSnapshot loop = trip(stopAt("A", 1, "SKIPPED"), stopAt("B", 2, "SCHEDULED"), stopAt("A", 3, "SCHEDULED"));

		assertThat(StopStatusCheck.evaluate(loop, 1)).isEqualTo(StopStatus.SKIPPED);
		assertThat(StopStatusCheck.evaluate(loop, 3)).isEqualTo(StopStatus.SCHEDULED);
	}

	private StopTimeContext stop(int sequence, String relationship) {
		return stopAt("stop-" + sequence, sequence, relationship);
	}

	private StopTimeContext stopAt(String stopId, int sequence, String relationship) {
		return new StopTimeContext(stopId, "Stop " + stopId, sequence, T0, T0, T0, T0, 0, 0, 0, relationship);
	}

	private TripSnapshot trip(StopTimeContext... stops) {
		return new TripSnapshot("t1", "10", 0, "shape", LocalDate.of(2026, 10, 3), 1000.0, List.of(stops),
				List.of(), T0, null, null, null, null, "SCHEDULED", false);
	}
}
