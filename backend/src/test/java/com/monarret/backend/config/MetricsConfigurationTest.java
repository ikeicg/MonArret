package com.monarret.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MetricsConfigurationTest {

	@Test
	void aTagFedWithUnboundedValues_cannotGrowTheRegistryPastTheLimit() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		registry.config().meterFilter(new MetricsConfiguration().meterLimit());

		for (int tripId = 0; tripId < MetricsConfiguration.MAX_METERS + 500; tripId++) {
			Counter.builder("per.trip").tag("tripId", String.valueOf(tripId)).register(registry).increment();
		}

		assertThat(registry.getMeters()).hasSize(MetricsConfiguration.MAX_METERS);
	}

	@Test
	void metersAlreadyRegistered_keepWorkingOnceTheLimitIsReached() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		registry.config().meterFilter(new MetricsConfiguration().meterLimit());
		Counter first = Counter.builder("per.trip").tag("tripId", "0").register(registry);
		for (int tripId = 1; tripId < MetricsConfiguration.MAX_METERS + 10; tripId++) {
			Counter.builder("per.trip").tag("tripId", String.valueOf(tripId)).register(registry);
		}

		first.increment();

		assertThat(registry.get("per.trip").tag("tripId", "0").counter().count()).isEqualTo(1);
	}
}
