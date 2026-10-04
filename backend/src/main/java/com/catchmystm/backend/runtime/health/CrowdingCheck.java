package com.catchmystm.backend.runtime.health;

import com.catchmystm.backend.runtime.model.TripSnapshot;

public final class CrowdingCheck {

	private CrowdingCheck() {
	}

	public static CrowdingLevel evaluate(TripSnapshot trip) {
		String occupancy = trip.occupancyStatus();
		if (occupancy == null) return CrowdingLevel.UNKNOWN;
		return switch (occupancy) {
			case "EMPTY", "MANY_SEATS_AVAILABLE" -> CrowdingLevel.MANY_SEATS;
			case "FEW_SEATS_AVAILABLE" -> CrowdingLevel.FEW_SEATS;
			case "STANDING_ROOM_ONLY", "CRUSHED_STANDING_ROOM_ONLY", "FULL" -> CrowdingLevel.CROWDED;
			default -> CrowdingLevel.UNKNOWN;
		};
	}
}
