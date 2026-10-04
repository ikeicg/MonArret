package com.catchmystm.backend.runtime.health;

import java.util.List;

import com.catchmystm.backend.runtime.model.GpsPing;
import com.catchmystm.backend.runtime.model.TripSnapshot;

public final class RouteStatusCheck {

	public static final double MAX_DISTANCE_METERS = 50.0;
	public static final int MIN_PINGS = 3;

	private RouteStatusCheck() {
	}

	// Judged on the window as it stands; a stale or frozen position keeps its last verdict.
	public static RouteStatus evaluate(TripSnapshot trip) {
		List<GpsPing> pings = trip.recentPings();
		if (pings.size() < MIN_PINGS) return RouteStatus.UNKNOWN;

		boolean allBeyond = true;
		for (GpsPing ping : pings) {
			if (ping.offRouteMeters() == null) return RouteStatus.UNKNOWN;
			if (ping.offRouteMeters() <= MAX_DISTANCE_METERS) allBeyond = false;
		}
		return allBeyond ? RouteStatus.NOT_ON_ROUTE : RouteStatus.ON_ROUTE;
	}
}
