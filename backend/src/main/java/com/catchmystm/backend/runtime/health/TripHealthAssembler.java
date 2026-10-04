package com.catchmystm.backend.runtime.health;

import java.time.Instant;

import com.catchmystm.backend.runtime.model.TripSnapshot;
import com.catchmystm.backend.service.FeedHealth;

public final class TripHealthAssembler {

	private TripHealthAssembler() {
	}

	public static TripHealth assemble(TripSnapshot trip, int stopSequence, Instant now, FeedHealth feedHealth) {
		Headline headline = StatusHeadlineCheck.evaluate(trip, now, feedHealth);

		if (headline.status() == HeadlineStatus.LIVE_DATA_UNAVAILABLE || headline.status() == HeadlineStatus.CANCELLED) {
			return build(trip, stopSequence, headline, null, null, null, null, null);
		}

		boolean hasVehicle = trip.latestPing() != null;
		BusProgress progress = hasVehicle ? BusProgressCheck.evaluate(trip, stopSequence) : null;
		boolean passed = progress != null && progress.state() == ProgressState.PASSED;
		StopTiming timing = passed ? null : TimingCheck.evaluate(trip, stopSequence, now);
		CrowdingLevel crowding = hasVehicle ? CrowdingCheck.evaluate(trip) : null;

		return build(trip, stopSequence, headline, StopStatusCheck.evaluate(trip, stopSequence), progress, timing,
				crowding, RouteStatusCheck.evaluate(trip));
	}

	private static TripHealth build(TripSnapshot trip, int stopSequence, Headline headline, StopStatus stopStatus,
			BusProgress progress, StopTiming timing, CrowdingLevel crowding, RouteStatus routeStatus) {
		return new TripHealth(trip.tripId(), trip.routeId(), trip.directionId(), stopSequence, trip.lastSeenInFeed(),
				headline, stopStatus, progress, timing, crowding, routeStatus);
	}
}
