package com.catchmystm.backend.runtime.model;

import java.time.Instant;

/**
 * A single vehicle position sample, already projected to UTM (EPSG:2959) meters —
 * not WGS84 degrees — so it's directly comparable to the stop/route geometry in
 * {@link com.catchmystm.backend.runtime.ActiveTripState}.
 */
public record GpsPing(double x, double y, Instant timestamp) {
}
