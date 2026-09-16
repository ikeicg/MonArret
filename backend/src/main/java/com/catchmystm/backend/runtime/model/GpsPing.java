package com.catchmystm.backend.runtime.model;

import java.time.Instant;

public record GpsPing(double latitude, double longitude, Instant timestamp ) {
}
