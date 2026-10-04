package com.catchmystm.backend.runtime.health;

import java.time.Instant;

// differenceSeconds is predicted minus scheduled (negative is early); estimate marks a prediction far enough
// ahead that it is still likely to move.
public record StopTiming(Instant scheduled, Instant predicted, TimingStatus status, Long differenceSeconds,
		boolean estimate) {
}
