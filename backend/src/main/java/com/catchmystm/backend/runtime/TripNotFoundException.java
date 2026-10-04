package com.catchmystm.backend.runtime;

// A trip the live feed names that is absent from the static schedule.
public class TripNotFoundException extends IllegalStateException {

	public TripNotFoundException(String tripId) {
		super("Trip not found: " + tripId);
	}
}
