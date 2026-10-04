package com.catchmystm.backend.runtime.health;

import com.catchmystm.backend.runtime.model.StopTimeContext;
import com.catchmystm.backend.runtime.model.TripSnapshot;

public final class StopStatusCheck {

	private StopStatusCheck() {
	}

	// Matched by stop sequence: a loop can visit the same stop id twice. Reports the last status the feed
	// stated, which holds even if the stop later drops out of the updates.
	public static StopStatus evaluate(TripSnapshot trip, int stopSequence) {
		for (StopTimeContext stop : trip.stopSchedule()) {
			if (stop.stopSequence() == null || stop.stopSequence() != stopSequence) continue;

			String stated = stop.scheduleRelationship();
			if (stated == null) return StopStatus.UNKNOWN;
			return switch (stated) {
				case "SKIPPED" -> StopStatus.SKIPPED;
				case "NO_DATA" -> StopStatus.NO_LIVE_INFO;
				case "SCHEDULED" -> StopStatus.SCHEDULED;
				default -> StopStatus.UNKNOWN;
			};
		}
		return StopStatus.UNKNOWN;
	}
}
