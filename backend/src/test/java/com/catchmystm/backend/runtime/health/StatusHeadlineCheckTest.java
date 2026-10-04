package com.catchmystm.backend.runtime.health;

import static com.catchmystm.backend.runtime.health.TripFixtures.T0;
import static com.catchmystm.backend.runtime.health.TripFixtures.fiveStops;
import static com.catchmystm.backend.runtime.health.TripFixtures.ping;
import static com.catchmystm.backend.runtime.health.TripFixtures.trip;
import static com.catchmystm.backend.runtime.health.TripFixtures.tripWithPing;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.catchmystm.backend.runtime.model.TripSnapshot;
import com.catchmystm.backend.service.FeedHealth;

class StatusHeadlineCheckTest {

	private static final Instant NOW = T0.plusSeconds(3600);

	private Headline headline(TripSnapshot trip, Instant now) {
		return StatusHeadlineCheck.evaluate(trip, now, FeedHealth.HEALTHY);
	}

	// --- feed health and cancellation ---

	@Test
	void feedDown_overridesEverythingElse() {
		TripSnapshot cancelled = trip(fiveStops(), List.of(), null, null, null, "CANCELED");

		assertThat(StatusHeadlineCheck.evaluate(cancelled, NOW, FeedHealth.DOWN).status())
				.isEqualTo(HeadlineStatus.LIVE_DATA_UNAVAILABLE);
	}

	@Test
	void feedStarting_isLiveDataUnavailable() {
		assertThat(StatusHeadlineCheck.evaluate(tripWithPing(NOW, 100.0, 1, "IN_TRANSIT_TO"), NOW, FeedHealth.STARTING)
				.status()).isEqualTo(HeadlineStatus.LIVE_DATA_UNAVAILABLE);
	}

	@Test
	void cancelled_winsOverALivePosition() {
		TripSnapshot trip = trip(fiveStops(), List.of(ping(NOW, 100.0)), 1, "IN_TRANSIT_TO", null, "CANCELED");

		assertThat(headline(trip, NOW).status()).isEqualTo(HeadlineStatus.CANCELLED);
	}

	// --- with a position ---

	@Test
	void freshPosition_isLive_withItsAge() {
		Headline result = headline(tripWithPing(NOW.minusSeconds(30), 1500.0, 2, "IN_TRANSIT_TO"), NOW);

		assertThat(result.status()).isEqualTo(HeadlineStatus.LIVE);
		assertThat(result.positionAgeSeconds()).isEqualTo(30);
	}

	@Test
	void positionExactlyAtTheStaleLimit_isStillLive() {
		assertThat(headline(tripWithPing(NOW.minusSeconds(120), 1500.0, 2, "IN_TRANSIT_TO"), NOW).status())
				.isEqualTo(HeadlineStatus.LIVE);
	}

	@Test
	void stalePositionMidRoute_isSignalLost() {
		Headline result = headline(tripWithPing(NOW.minusSeconds(121), 1500.0, 2, "IN_TRANSIT_TO"), NOW);

		assertThat(result.status()).isEqualTo(HeadlineStatus.SIGNAL_LOST);
		assertThat(result.positionAgeSeconds()).isEqualTo(121);
	}

	@Test
	void stalePositionInTheLastFivePercent_isTripFinished() {
		assertThat(headline(tripWithPing(NOW.minusSeconds(300), 3900.0, 5, "IN_TRANSIT_TO"), NOW).status())
				.isEqualTo(HeadlineStatus.TRIP_FINISHED);
	}

	@Test
	void stalePositionStoppedAtTheFinalStop_isTripFinished() {
		assertThat(headline(tripWithPing(NOW.minusSeconds(300), 3000.0, 5, "STOPPED_AT"), NOW).status())
				.isEqualTo(HeadlineStatus.TRIP_FINISHED);
	}

	@Test
	void stalePositionHeadingToTheFinalStop_isSignalLost() {
		assertThat(headline(tripWithPing(NOW.minusSeconds(300), 3000.0, 5, "IN_TRANSIT_TO"), NOW).status())
				.isEqualTo(HeadlineStatus.SIGNAL_LOST);
	}

	// --- no vehicle: measured from the first stop's scheduled departure (T0) ---

	@Test
	void noVehicle_beforeTheDeparture_isNotTrackedYet() {
		assertThat(headline(trip(fiveStops(), List.of(), null, null, null, "SCHEDULED"), T0.minusSeconds(300)).status())
				.isEqualTo(HeadlineStatus.NOT_TRACKED_YET);
	}

	@Test
	void noVehicle_twoMinutesPast_isStillNotTrackedYet() {
		assertThat(headline(trip(fiveStops(), List.of(), null, null, null, "SCHEDULED"), T0.plusSeconds(120)).status())
				.isEqualTo(HeadlineStatus.NOT_TRACKED_YET);
	}

	@Test
	void noVehicle_justOverTwoMinutesPast_isStartedLate() {
		assertThat(headline(trip(fiveStops(), List.of(), null, null, null, "SCHEDULED"), T0.plusSeconds(121)).status())
				.isEqualTo(HeadlineStatus.STARTED_LATE);
	}

	@Test
	void noVehicle_tenMinutesPast_isStillStartedLate() {
		assertThat(headline(trip(fiveStops(), List.of(), null, null, null, "SCHEDULED"), T0.plusSeconds(600)).status())
				.isEqualTo(HeadlineStatus.STARTED_LATE);
	}

	@Test
	void noVehicle_overTenMinutesPast_isNoLiveBusFound() {
		assertThat(headline(trip(fiveStops(), List.of(), null, null, null, "SCHEDULED"), T0.plusSeconds(601)).status())
				.isEqualTo(HeadlineStatus.NO_LIVE_BUS_FOUND);
	}

	@Test
	void noVehicleAndNoSchedule_isNoLiveBusFound() {
		assertThat(headline(trip(List.of(), List.of(), null, null, null, "SCHEDULED"), NOW).status())
				.isEqualTo(HeadlineStatus.NO_LIVE_BUS_FOUND);
	}
}
