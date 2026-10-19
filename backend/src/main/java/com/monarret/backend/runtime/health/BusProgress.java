package com.monarret.backend.runtime.health;

// stopsAway and nextStopName are set only for BEFORE_STOP.
public record BusProgress(ProgressState state, Integer stopsAway, String nextStopName, MovementStatus movement,
		Long stationarySeconds) {

	public BusProgress(ProgressState state, Integer stopsAway, String nextStopName, MovementStatus movement) {
		this(state, stopsAway, nextStopName, movement, null);
	}

	// Minutes spent sitting in one place outrank the short-window movement reading, which a wobbling GPS can flip.
	public BusProgress withStationarySeconds(Long seconds) {
		return seconds == null ? this : new BusProgress(state, stopsAway, nextStopName, MovementStatus.STATIONARY, seconds);
	}
}
