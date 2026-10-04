package com.catchmystm.backend.runtime.health;

import static com.catchmystm.backend.runtime.health.TripFixtures.T0;
import static com.catchmystm.backend.runtime.health.TripFixtures.fiveStops;
import static com.catchmystm.backend.runtime.health.TripFixtures.fiveStopsWith;
import static com.catchmystm.backend.runtime.health.TripFixtures.ping;
import static com.catchmystm.backend.runtime.health.TripFixtures.trip;
import static com.catchmystm.backend.runtime.health.TripFixtures.tripWithPing;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.catchmystm.backend.runtime.model.TripSnapshot;

// The rider's stop is stop 4, 3000 m along the route.
class BusProgressCheckTest {

	private static final int RIDER_STOP = 4;

	private BusProgress progress(TripSnapshot trip) {
		return BusProgressCheck.evaluate(trip, RIDER_STOP);
	}

	@Test
	void noPosition_isUnknown() {
		assertThat(progress(trip(fiveStops(), List.of(), null, null, null, "SCHEDULED")).state())
				.isEqualTo(ProgressState.UNKNOWN);
	}

	@Test
	void riderStopNotInTheSchedule_isUnknown() {
		assertThat(BusProgressCheck.evaluate(tripWithPing(T0, 500.0, 2, "IN_TRANSIT_TO"), 99).state())
				.isEqualTo(ProgressState.UNKNOWN);
	}

	@Test
	void stoppedAtTheRidersStop_isAtStop() {
		assertThat(progress(tripWithPing(T0, 3000.0, 4, "STOPPED_AT")).state()).isEqualTo(ProgressState.AT_STOP);
	}

	@Test
	void inTransitToAnEarlierStop_countsStopsFromThatStopThroughTheRidersStop() {
		BusProgress result = progress(tripWithPing(T0, 1500.0, 2, "IN_TRANSIT_TO"));

		assertThat(result.state()).isEqualTo(ProgressState.BEFORE_STOP);
		assertThat(result.stopsAway()).isEqualTo(3);
		assertThat(result.nextStopName()).isEqualTo("Stop 2");
	}

	@Test
	void stoppedAtAnEarlierStop_nextStopIsTheOneAfter() {
		BusProgress result = progress(tripWithPing(T0, 1000.0, 2, "STOPPED_AT"));

		assertThat(result.stopsAway()).isEqualTo(2);
		assertThat(result.nextStopName()).isEqualTo("Stop 3");
	}

	@Test
	void headingToTheRidersStop_isOneStopAway() {
		BusProgress result = progress(tripWithPing(T0, 2500.0, 4, "IN_TRANSIT_TO"));

		assertThat(result.state()).isEqualTo(ProgressState.BEFORE_STOP);
		assertThat(result.stopsAway()).isEqualTo(1);
		assertThat(result.nextStopName()).isEqualTo("Stop 4");
	}

	@Test
	void skippedStops_areNotCounted_andCannotBeTheNextStop() {
		TripSnapshot trip = trip(fiveStopsWith(2, "SKIPPED"), List.of(ping(T0, 500.0)), 2, "IN_TRANSIT_TO", null,
				"SCHEDULED");

		BusProgress result = progress(trip);

		assertThat(result.stopsAway()).isEqualTo(2);
		assertThat(result.nextStopName()).isEqualTo("Stop 3");
	}

	@Test
	void positionAndFeedBothAhead_isPassed() {
		assertThat(progress(tripWithPing(T0, 3500.0, 5, "IN_TRANSIT_TO")).state()).isEqualTo(ProgressState.PASSED);
	}

	@Test
	void onlyTheFeedAhead_mayHavePassed() {
		assertThat(progress(tripWithPing(T0, 2900.0, 5, "IN_TRANSIT_TO")).state())
				.isEqualTo(ProgressState.MAY_HAVE_PASSED);
	}

	@Test
	void onlyThePositionAhead_mayHavePassed() {
		assertThat(progress(tripWithPing(T0, 3100.0, 4, "IN_TRANSIT_TO")).state())
				.isEqualTo(ProgressState.MAY_HAVE_PASSED);
	}

	@Test
	void positionWithinNoiseOfTheStop_isNotPassed() {
		BusProgress result = progress(tripWithPing(T0, 3010.0, 4, "IN_TRANSIT_TO"));

		assertThat(result.state()).isEqualTo(ProgressState.BEFORE_STOP);
		assertThat(result.stopsAway()).isEqualTo(1);
	}

	@Test
	void withoutAStopSequence_theNextStopComesFromThePosition() {
		BusProgress result = progress(tripWithPing(T0, 1500.0, null, null));

		assertThat(result.state()).isEqualTo(ProgressState.BEFORE_STOP);
		assertThat(result.stopsAway()).isEqualTo(2);
		assertThat(result.nextStopName()).isEqualTo("Stop 3");
	}

	@Test
	void withoutAStopSequenceOrRouteDistance_isUnknown() {
		assertThat(progress(tripWithPing(T0, null, null, null)).state()).isEqualTo(ProgressState.UNKNOWN);
	}

	@Test
	void carriesTheMovementStatus() {
		TripSnapshot moving = trip(fiveStops(), List.of(ping(T0, 100.0), ping(T0.plusSeconds(20), 400.0)), 2,
				"IN_TRANSIT_TO", null, "SCHEDULED");

		assertThat(progress(moving).movement()).isEqualTo(MovementStatus.MOVING);
	}
}
