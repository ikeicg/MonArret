package com.catchmystm.backend.runtime.health;

public enum HeadlineStatus {
	// Our own poller is failing
	LIVE_DATA_UNAVAILABLE,
	CANCELLED,
	TRIP_FINISHED,
	SIGNAL_LOST,
	LIVE,
	NOT_TRACKED_YET,
	STARTED_LATE,
	NO_LIVE_BUS_FOUND,
	// The trip is not in the live feed (not yet in it, or no longer)
	NO_LIVE_INFO
}
