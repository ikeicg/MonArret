package com.monarret.backend.runtime.health;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.monarret.backend.runtime.model.GpsPing;
import com.monarret.backend.runtime.model.StopTimeContext;
import com.monarret.backend.runtime.model.TripSnapshot;

// A five-stop, 4 km trip: stop N sits at (N - 1) km and is scheduled (N - 1) * 10 minutes after T0.
final class TripFixtures {

	static final Instant T0 = Instant.parse("2026-10-03T12:00:00Z");
	static final double ROUTE_LENGTH = 4000.0;

	private TripFixtures() {
	}

	static StopTimeContext stop(int sequence, String relationship) {
		Instant scheduled = T0.plusSeconds(600L * (sequence - 1));
		return new StopTimeContext("stop-" + sequence, "Stop " + sequence, sequence, scheduled, scheduled, scheduled,
				scheduled, 0, 0, 1000.0 * (sequence - 1), relationship);
	}

	static StopTimeContext stopWithEstimate(int sequence, Instant estimatedDeparture) {
		StopTimeContext base = stop(sequence, "SCHEDULED");
		return new StopTimeContext(base.stopId(), base.stopName(), sequence, base.scheduledArrival(),
				estimatedDeparture, base.scheduledDeparture(), estimatedDeparture, 0, 0, base.distanceAlongRoute(),
				"SCHEDULED");
	}

	static List<StopTimeContext> fiveStops() {
		return fiveStopsWith(0, null);
	}

	// Replaces stop 'sequence' of the five with a different one.
	static List<StopTimeContext> fiveStopsWith(int sequence, String relationship) {
		List<StopTimeContext> stops = new ArrayList<>();
		for (int i = 1; i <= 5; i++) {
			stops.add(stop(i, i == sequence ? relationship : null));
		}
		return stops;
	}

	static List<StopTimeContext> fiveStopsReplacing(StopTimeContext replacement) {
		List<StopTimeContext> stops = new ArrayList<>(fiveStops());
		stops.set(replacement.stopSequence() - 1, replacement);
		return stops;
	}

	static GpsPing ping(Instant at, Double alongRoute) {
		return new GpsPing(alongRoute == null ? 0 : alongRoute, 0, at, alongRoute, 0.0);
	}

	static GpsPing pingOffRoute(Instant at, double alongRoute, double offRouteMeters) {
		return new GpsPing(alongRoute, 0, at, alongRoute, offRouteMeters);
	}

	static TripSnapshot trip(List<StopTimeContext> stops, List<GpsPing> pings, Integer currentSequence, String status,
			String occupancy, String tripRelationship) {
		return new TripSnapshot("t1", "10", 0, "shape", LocalDate.of(2026, 10, 3), ROUTE_LENGTH, stops, pings, T0,
				currentSequence, currentSequence == null ? null : "stop-" + currentSequence, status, occupancy,
				tripRelationship, false);
	}

	static TripSnapshot tripWithPing(Instant at, Double alongRoute, Integer currentSequence, String status) {
		return trip(fiveStops(), List.of(ping(at, alongRoute)), currentSequence, status, null, "SCHEDULED");
	}
}
