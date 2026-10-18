package com.monarret.backend.runtime.health;

public enum ProgressState {
	BEFORE_STOP,
	AT_STOP,
	PASSED,
	// One of the position and feed checks says passed, the other does not
	MAY_HAVE_PASSED,
	UNKNOWN
}
