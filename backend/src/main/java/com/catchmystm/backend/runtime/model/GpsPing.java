package com.catchmystm.backend.runtime.model;

import java.time.Instant;

/**
 * A single vehicle position sample, already projected to UTM (EPSG:2959) meters —
 * not WGS84 degrees — so it's directly comparable to the stop/route geometry in
 * {@link com.catchmystm.backend.runtime.ActiveTripState}.
 * {@code alongRoute} is meters along the trip's route line and {@code offRouteMeters} the distance
 * from the whole route line; both are null when the state has no route.
 */
public record GpsPing(double x, double y, Instant timestamp, Double alongRoute, Double offRouteMeters) {

	// Meters between two pings: along the route when both have it, straight-line otherwise.
	public static double metersBetween(GpsPing first, GpsPing last) {
		if (first.alongRoute() != null && last.alongRoute() != null) {
			return Math.abs(last.alongRoute() - first.alongRoute());
		}
		return Math.hypot(last.x() - first.x(), last.y() - first.y());
	}
}
