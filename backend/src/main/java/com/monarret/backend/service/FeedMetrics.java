package com.monarret.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.monarret.backend.runtime.ActiveTripStateStore;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/** Gauges read on demand: the store size, the age of each feed's last successful fetch and the registry's own size. */
@Component
public class FeedMetrics implements MeterBinder {

	private final StmPoller poller;
	private final ActiveTripStateStore store;

	public FeedMetrics(StmPoller poller, ActiveTripStateStore store) {
		this.poller = poller;
		this.store = store;
	}

	@Override
	public void bindTo(MeterRegistry registry) {
		Gauge.builder("trips.active", store, ActiveTripStateStore::getActiveTripCount)
				.description("Trips currently held in the live store")
				.register(registry);
		Gauge.builder("metrics.meters", registry, r -> r.getMeters().size())
				.description("Meters in this registry, to watch against the configured limit")
				.register(registry);
		ageGauge(registry, "vehiclePositions", poller::getLastVehiclePositionsSuccess);
		ageGauge(registry, "tripUpdates", poller::getLastTripUpdatesSuccess);
	}

	private static void ageGauge(MeterRegistry registry, String feed, Supplier<Instant> lastSuccess) {
		Gauge.builder("feed.age.seconds", () -> {
			Instant last = lastSuccess.get();
			return last == null ? Double.NaN : Duration.between(last, Instant.now()).toSeconds();
		}).description("Seconds since the feed was last fetched successfully").tag("feed", feed).register(registry);
	}
}
