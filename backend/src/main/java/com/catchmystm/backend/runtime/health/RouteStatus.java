package com.catchmystm.backend.runtime.health;

public enum RouteStatus {
	ON_ROUTE,
	NOT_ON_ROUTE,
	// Too few pings, or the trip has no route geometry
	UNKNOWN
}
