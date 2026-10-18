package com.monarret.backend.runtime.health;

// The feed's occupancy values, one for one.
public enum Occupancy {
	EMPTY,
	MANY_SEATS_AVAILABLE,
	FEW_SEATS_AVAILABLE,
	STANDING_ROOM_ONLY,
	CRUSHED_STANDING_ROOM_ONLY,
	FULL,
	NOT_ACCEPTING_PASSENGERS,
	NO_DATA_AVAILABLE,
	NOT_BOARDABLE,
	// Absent from the message, or a value this version does not know
	UNKNOWN;

	public static Occupancy fromFeed(String feedValue) {
		if (feedValue == null) return UNKNOWN;
		try {
			return valueOf(feedValue);
		} catch (IllegalArgumentException e) {
			return UNKNOWN;
		}
	}
}
