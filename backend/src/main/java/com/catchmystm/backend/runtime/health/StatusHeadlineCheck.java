package com.catchmystm.backend.runtime.health;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.catchmystm.backend.runtime.model.GpsPing;
import com.catchmystm.backend.runtime.model.StopTimeContext;
import com.catchmystm.backend.runtime.model.TripSnapshot;
import com.catchmystm.backend.service.FeedHealth;

public final class StatusHeadlineCheck {

	static final Duration STALE_AFTER = Duration.ofSeconds(120);
	static final double END_OF_ROUTE_FRACTION = 0.95;
	static final Duration NOT_TRACKED_GRACE = Duration.ofMinutes(2);
	static final Duration STARTED_LATE_LIMIT = Duration.ofMinutes(10);

	private StatusHeadlineCheck() {
	}

	// Precedence: feed health, cancelled, position state, then the no-vehicle timings.
	public static Headline evaluate(TripSnapshot trip, Instant now, FeedHealth feedHealth) {
		if (feedHealth != FeedHealth.HEALTHY) return new Headline(HeadlineStatus.LIVE_DATA_UNAVAILABLE, null);
		if ("CANCELED".equals(trip.scheduleRelationship())) return new Headline(HeadlineStatus.CANCELLED, null);

		GpsPing ping = trip.latestPing();
		if (ping == null) return new Headline(noVehicleStatus(trip, now), null);

		Duration age = Duration.between(ping.timestamp(), now);
		long ageSeconds = Math.max(0, age.getSeconds());
		if (age.compareTo(STALE_AFTER) <= 0) return new Headline(HeadlineStatus.LIVE, ageSeconds);

		HeadlineStatus stale = nearEndOfTrip(trip, ping) ? HeadlineStatus.TRIP_FINISHED : HeadlineStatus.SIGNAL_LOST;
		return new Headline(stale, ageSeconds);
	}

	// The last ~5% of the route, or stopped at the final stop.
	private static boolean nearEndOfTrip(TripSnapshot trip, GpsPing ping) {
		Double routeLength = trip.routeLengthMeters();
		if (ping.alongRoute() != null && routeLength != null && routeLength > 0
				&& ping.alongRoute() >= END_OF_ROUTE_FRACTION * routeLength) {
			return true;
		}

		List<StopTimeContext> schedule = trip.stopSchedule();
		if (schedule.isEmpty() || trip.currentStopSequence() == null) return false;
		Integer finalSequence = schedule.get(schedule.size() - 1).stopSequence();
		return trip.currentStopSequence().equals(finalSequence) && "STOPPED_AT".equals(trip.currentStatus());
	}

	// Measured from the first stop's scheduled departure: the feed's own time slides later while no vehicle
	// exists, so it would never run out.
	private static HeadlineStatus noVehicleStatus(TripSnapshot trip, Instant now) {
		List<StopTimeContext> schedule = trip.stopSchedule();
		if (schedule.isEmpty()) return HeadlineStatus.NO_LIVE_BUS_FOUND;

		StopTimeContext first = schedule.get(0);
		Instant start = first.scheduledDeparture() != null ? first.scheduledDeparture() : first.scheduledArrival();
		Duration pastStart = Duration.between(start, now);

		if (pastStart.compareTo(NOT_TRACKED_GRACE) <= 0) return HeadlineStatus.NOT_TRACKED_YET;
		if (pastStart.compareTo(STARTED_LATE_LIMIT) <= 0) return HeadlineStatus.STARTED_LATE;
		return HeadlineStatus.NO_LIVE_BUS_FOUND;
	}
}
