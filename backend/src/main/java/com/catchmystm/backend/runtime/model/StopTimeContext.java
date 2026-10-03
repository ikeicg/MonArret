package com.catchmystm.backend.runtime.model;

import java.time.Instant;

/**
 * x/y are UTM (EPSG:2959) meters, not WGS84 degrees — see
 * {@link com.catchmystm.backend.runtime.ActiveTripStateFactory}.
 * Scheduled times are resolved against the trip's service date; estimated times
 * come from the feed's epoch seconds. Both are absolute instants.
 * distanceAlongRoute is meters from the start of the trip's route line to the stop.
 */
public record StopTimeContext(
		String stopId,
	    Integer stopSequence,
	    Instant scheduledArrival,
	    Instant estimatedArrival,
	    Instant scheduledDeparture,
	    Instant estimatedDeparture,
	    double x,
	    double y,
	    double distanceAlongRoute,
	    String scheduleRelationship) {

}
