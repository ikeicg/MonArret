package com.catchmystm.backend.runtime.health;

// stopsAway and nextStopName are set only for BEFORE_STOP.
public record BusProgress(ProgressState state, Integer stopsAway, String nextStopName, MovementStatus movement) {
}
