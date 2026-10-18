package com.monarret.backend.runtime.health;

import java.time.Instant;

// The Trip Health page payload. Items that do not apply are null: a cancelled trip or unavailable live data
// carries the headline only; timing and crowding need a live vehicle.
public record TripHealth(
		String tripId,
		String routeId,
		Integer directionId,
		int stopSequence,
		Instant updatedAt,
		Headline headline,
		StopStatus stopStatus,
		BusProgress progress,
		StopTiming timing,
		Occupancy crowding,
		RouteStatus routeStatus) {
}
