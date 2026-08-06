package com.catchmystm.backend.dto;

public record NextDepartureRequest(
		String routeId, 
		int directionId, 
		String stopId) {

}
