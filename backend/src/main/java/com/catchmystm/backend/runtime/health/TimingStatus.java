package com.catchmystm.backend.runtime.health;

public enum TimingStatus {
	EARLY,
	LATE,
	// Within the early/late highlight limits
	NORMAL,
	NO_PREDICTION
}
