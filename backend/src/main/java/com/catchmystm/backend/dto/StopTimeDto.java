package com.catchmystm.backend.dto;

import java.time.LocalDateTime;

public record StopTimeDto(
		String tripId,
		String stopId,
		String stopName,
		LocalDateTime departureTime,
		Integer stopSequence
		) {

}
