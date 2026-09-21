package com.catchmystm.backend.runtime.model;

import java.time.LocalTime;

/**
 * x/y are UTM (EPSG:2959) meters, not WGS84 degrees — see
 * {@link com.catchmystm.backend.runtime.ActiveTripStateFactory}.
 */
public record StopTimeContext(
		String stopId,
	    Integer stopSequence,
	    LocalTime scheduledArrival,
	    LocalTime estimatedArrival,
	    LocalTime scheduledDeparture,
	    LocalTime estimatedDeparture,
	    double x,
	    double y,
	    String scheduleRelationship) {

}
