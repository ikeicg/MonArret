package com.catchmystm.backend.runtime.model;

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
		boolean positionFrozen,
		Double bunchingGapSeconds,
		int consecutiveCloseCycles,
		boolean bunchingConfirmed) {

	public GpsPing latestPing() {
		return recentPings.isEmpty() ? null : recentPings.get(recentPings.size() - 1);
	}
}
