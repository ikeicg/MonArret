package com.catchmystm.backend.runtime.health;

import java.time.Duration;
import java.time.Instant;

import com.catchmystm.backend.runtime.model.StopTimeContext;
import com.catchmystm.backend.runtime.model.TripSnapshot;

public final class TimingCheck {

	static final Duration EARLY_LIMIT = Duration.ofMinutes(2);
	static final Duration LATE_LIMIT = Duration.ofMinutes(5);
	static final Duration ESTIMATE_AFTER = Duration.ofMinutes(5);

	private TimingCheck() {
	}

	// Null without a live vehicle: the feed's time for a trip with no vehicle is schedule-derived, not a prediction.
	// The departure time is used, falling back to the arrival time.
	public static StopTiming evaluate(TripSnapshot trip, int riderStopSequence, Instant now) {
		if (trip.latestPing() == null) return null;

		StopTimeContext stop = null;
		for (StopTimeContext candidate : trip.stopSchedule()) {
			if (candidate.stopSequence() != null && candidate.stopSequence() == riderStopSequence) {
				stop = candidate;
				break;
			}
		}
		if (stop == null) return null;

		Instant scheduled = stop.scheduledDeparture() != null ? stop.scheduledDeparture() : stop.scheduledArrival();
		Instant predicted = stop.estimatedDeparture() != null ? stop.estimatedDeparture() : stop.estimatedArrival();
		if (predicted == null) return new StopTiming(scheduled, null, TimingStatus.NO_PREDICTION, null, false);

		Duration difference = Duration.between(scheduled, predicted);
		TimingStatus status = difference.compareTo(EARLY_LIMIT.negated()) <= 0 ? TimingStatus.EARLY
				: difference.compareTo(LATE_LIMIT) >= 0 ? TimingStatus.LATE
				: TimingStatus.NORMAL;
		boolean estimate = Duration.between(now, predicted).compareTo(ESTIMATE_AFTER) > 0;
		return new StopTiming(scheduled, predicted, status, difference.getSeconds(), estimate);
	}
}
