package com.monarret.backend.runtime.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;


public record TripSnapshot(
		String tripId,
		String routeId,
		Integer directionId,
		String shapeId,
		LocalDate serviceDate,
		Double routeLengthMeters,
		List<StopTimeContext> stopSchedule,
		List<GpsPing> recentPings,
		Instant lastSeenInFeed,
		Integer currentStopSequence,
		String currentStopId,
		String currentStatus,
		String occupancyStatus,
		String scheduleRelationship,
		boolean positionFrozen) {

	public GpsPing latestPing() {
		return recentPings.isEmpty() ? null : recentPings.get(recentPings.size() - 1);
	}

	// Meters moved between the oldest and newest ping; null with fewer than two pings.
	public Double metersMovedInWindow() {
		if (recentPings.size() < 2) return null;
		return GpsPing.metersBetween(recentPings.get(0), latestPing());
	}
}
