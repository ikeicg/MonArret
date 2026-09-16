package com.catchmystm.backend.runtime.model;

import java.time.LocalTime;

public record StopTimeContext(
		String stopId,
	    Integer stopSequence,
	    LocalTime scheduledArrival,
	    LocalTime estimatedArrival, 
	    LocalTime scheduledDeparture,
	    LocalTime estimatedDeparture,
	    double longitude,
	    double latitude) {

}
