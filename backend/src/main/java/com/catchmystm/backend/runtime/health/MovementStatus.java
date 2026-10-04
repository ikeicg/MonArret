package com.catchmystm.backend.runtime.health;

public enum MovementStatus {
	MOVING,
	// Has not moved for the whole ping window
	STATIONARY,
	// Too few pings, or a window too short to call it stationary
	UNKNOWN
}
