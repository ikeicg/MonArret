package com.monarret.backend.runtime.health;

public enum StopStatus {
	SKIPPED,
	NO_LIVE_INFO,
	SCHEDULED,
	// The feed has not stated anything for the stop, or the stop is not in the trip's schedule
	UNKNOWN
}
