package com.monarret.backend.runtime.health;

import java.time.Duration;
import java.time.Instant;

import com.monarret.backend.runtime.model.GpsPing;
import com.monarret.backend.runtime.model.TripSnapshot;

public final class StationaryCheck {

	// A stop at a light or a stop is not worth a line; two minutes is.
	static final Duration MIN_SHOWN = Duration.ofMinutes(2);

	private StationaryCheck() {
	}

	// Seconds the bus has sat in one place as of its latest position; null when it has not sat long enough, or when
	// the latest position is too old to say where the bus is now.
	public static Long seconds(TripSnapshot trip, Instant now) {
		GpsPing latest = trip.latestPing();
		Instant since = trip.stationarySince();
		if (latest == null || since == null) return null;
		if (Duration.between(latest.timestamp(), now).compareTo(StatusHeadlineCheck.STALE_AFTER) > 0) return null;
		Duration still = Duration.between(since, latest.timestamp());
		return still.compareTo(MIN_SHOWN) >= 0 ? still.getSeconds() : null;
	}
}
