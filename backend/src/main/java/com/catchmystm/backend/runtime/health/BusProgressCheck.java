package com.catchmystm.backend.runtime.health;

import java.util.List;

import com.catchmystm.backend.runtime.model.GpsPing;
import com.catchmystm.backend.runtime.model.StopTimeContext;
import com.catchmystm.backend.runtime.model.TripSnapshot;

public final class BusProgressCheck {

	// Keeps a bus stopped at the stop, whose position sits within GPS noise of it, from reading as passed.
	static final double PASSED_TOLERANCE_METERS = 20.0;

	private BusProgressCheck() {
	}

	// Positions in the ordered stop list are used throughout, never differences of sequence numbers.
	public static BusProgress evaluate(TripSnapshot trip, int riderStopSequence) {
		MovementStatus movement = MovementCheck.evaluate(trip);
		List<StopTimeContext> schedule = trip.stopSchedule();
		GpsPing ping = trip.latestPing();
		int riderIndex = indexOf(schedule, riderStopSequence);
		if (ping == null || riderIndex < 0) return new BusProgress(ProgressState.UNKNOWN, null, null, movement);

		int currentIndex = trip.currentStopSequence() == null ? -1 : indexOf(schedule, trip.currentStopSequence());
		boolean stoppedAt = "STOPPED_AT".equals(trip.currentStatus());
		if (stoppedAt && currentIndex == riderIndex) return progress(ProgressState.AT_STOP, movement);

		double riderDistance = schedule.get(riderIndex).distanceAlongRoute();
		boolean positionAhead = ping.alongRoute() != null
				&& ping.alongRoute() > riderDistance + PASSED_TOLERANCE_METERS;
		boolean feedAhead = currentIndex > riderIndex;
		if (positionAhead && feedAhead) return progress(ProgressState.PASSED, movement);
		if (positionAhead || feedAhead) return progress(ProgressState.MAY_HAVE_PASSED, movement);

		// IN_TRANSIT_TO and INCOMING_AT mean the current stop is the next one; STOPPED_AT means the one after it.
		int nextIndex;
		if (currentIndex >= 0) {
			nextIndex = stoppedAt ? currentIndex + 1 : currentIndex;
		} else if (ping.alongRoute() != null) {
			nextIndex = firstStopBeyond(schedule, ping.alongRoute());
		} else {
			return progress(ProgressState.UNKNOWN, movement);
		}
		if (nextIndex > riderIndex) return progress(ProgressState.MAY_HAVE_PASSED, movement);

		int stopsAway = 0;
		String nextStopName = null;
		for (int i = nextIndex; i <= riderIndex; i++) {
			if (isSkipped(schedule.get(i))) continue;
			if (nextStopName == null) nextStopName = schedule.get(i).stopName();
			stopsAway++;
		}
		return new BusProgress(ProgressState.BEFORE_STOP, stopsAway, nextStopName, movement);
	}

	private static BusProgress progress(ProgressState state, MovementStatus movement) {
		return new BusProgress(state, null, null, movement);
	}

	private static boolean isSkipped(StopTimeContext stop) {
		return "SKIPPED".equals(stop.scheduleRelationship());
	}

	private static int indexOf(List<StopTimeContext> schedule, int stopSequence) {
		for (int i = 0; i < schedule.size(); i++) {
			Integer sequence = schedule.get(i).stopSequence();
			if (sequence != null && sequence == stopSequence) return i;
		}
		return -1;
	}

	private static int firstStopBeyond(List<StopTimeContext> schedule, double alongRoute) {
		for (int i = 0; i < schedule.size(); i++) {
			if (schedule.get(i).distanceAlongRoute() > alongRoute) return i;
		}
		return schedule.size();
	}
}
