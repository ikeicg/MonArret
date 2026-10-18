package com.monarret.backend.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.monarret.backend.runtime.ActiveTripStateStore;
import com.monarret.backend.runtime.LiveStateEngine;

/**
 * The "liveFeed" section of the health report. A stale feed is reported as a detail and never takes the app DOWN:
 * the schedule still works, and restarting the app would not bring STM back.
 */
@Component("liveFeed")
public class LiveFeedHealthIndicator implements HealthIndicator {

	private final StmPoller poller;
	private final ActiveTripStateStore store;
	private final LiveStateEngine engine;

	public LiveFeedHealthIndicator(StmPoller poller, ActiveTripStateStore store, LiveStateEngine engine) {
		this.poller = poller;
		this.store = store;
		this.engine = engine;
	}

	@Override
	public Health health() {
		return health(Instant.now());
	}

	Health health(Instant now) {
		Health.Builder health = Health.up()
				.withDetail("feed", poller.getFeedHealth(now))
				.withDetail("activeTrips", store.getActiveTripCount())
				.withDetail("feedVersion", engine.feedVersion());
		addAge(health, "vehiclePositionsAgeSeconds", poller.getLastVehiclePositionsSuccess(), now);
		addAge(health, "tripUpdatesAgeSeconds", poller.getLastTripUpdatesSuccess(), now);
		addMillis(health, "lastTripUpdatesIngestMs", engine.lastTripUpdatesIngestMillis());
		addMillis(health, "lastVehiclePositionsIngestMs", engine.lastVehiclePositionsIngestMillis());
		addMillis(health, "firstTripUpdatesIngestMs", engine.firstTripUpdatesIngestMillis());
		addMillis(health, "firstVehiclePositionsIngestMs", engine.firstVehiclePositionsIngestMillis());
		return health.build();
	}

	private static void addMillis(Health.Builder health, String name, long millis) {
		if (millis >= 0) {
			health.withDetail(name, millis);
		}
	}

	private static void addAge(Health.Builder health, String name, Instant lastSuccess, Instant now) {
		if (lastSuccess != null) {
			health.withDetail(name, Duration.between(lastSuccess, now).toSeconds());
		}
	}
}
