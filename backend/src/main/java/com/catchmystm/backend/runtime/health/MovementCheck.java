package com.catchmystm.backend.runtime.health;

import java.time.Duration;

import com.catchmystm.backend.runtime.ActiveTripState;
import com.catchmystm.backend.runtime.model.TripSnapshot;

public final class MovementCheck {

	// A full window is trimmed to PING_WINDOW, so one poll short of it still counts as the whole window.
	static final Duration MIN_STATIONARY_SPAN = ActiveTripState.PING_WINDOW.minusSeconds(20);

	private MovementCheck() {
	}

	public static MovementStatus evaluate(TripSnapshot trip) {
		Double moved = trip.metersMovedInWindow();
		if (moved == null) return MovementStatus.UNKNOWN;
		if (moved >= ActiveTripState.FROZEN_MOVEMENT_METERS) return MovementStatus.MOVING;

		Duration span = Duration.between(trip.recentPings().get(0).timestamp(), trip.latestPing().timestamp());
		return span.compareTo(MIN_STATIONARY_SPAN) >= 0 ? MovementStatus.STATIONARY : MovementStatus.UNKNOWN;
	}
}
