package com.catchmystm.backend.dto;

public record NextDepartureDto(
		String routeId, 
		int directionId, 
		String stopId, 
		String tripId,
		int effectiveDepartureTime,
		int dayOffset
		) {

}
