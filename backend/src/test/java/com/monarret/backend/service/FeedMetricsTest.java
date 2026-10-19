package com.monarret.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import com.monarret.backend.config.PollProperties;
import com.monarret.backend.runtime.ActiveTripStateStore;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class FeedMetricsTest {

	@Mock
	private ActiveTripStateStore store;

	private StmPoller poller;
	private SimpleMeterRegistry registry;

	@BeforeEach
	void setUp() {
		poller = new StmPoller(WebClient.builder(), "http://localhost", "key", new PollProperties(Duration.ofSeconds(20)));
		registry = new SimpleMeterRegistry();
		new FeedMetrics(poller, store).bindTo(registry);
	}

	@Test
	void activeTrips_followsTheStore() {
		when(store.getActiveTripCount()).thenReturn(2300);

		assertThat(registry.get("trips.active").gauge().value()).isEqualTo(2300);
	}

	@Test
	void meterCount_reportsTheRegistrySize() {
		assertThat(registry.get("metrics.meters").gauge().value()).isEqualTo(registry.getMeters().size());
	}

	@Test
	void feedAge_isNaNUntilTheFeedHasBeenFetched() {
		assertThat(registry.get("feed.age.seconds").tag("feed", "tripUpdates").gauge().value()).isNaN();
		assertThat(registry.get("feed.age.seconds").tag("feed", "vehiclePositions").gauge().value()).isNaN();
	}

	@Test
	void feedAge_countsSecondsSinceTheLastSuccess() {
		poller.markTripUpdatesReceived(Instant.now().minusSeconds(30));

		assertThat(registry.get("feed.age.seconds").tag("feed", "tripUpdates").gauge().value())
				.isGreaterThanOrEqualTo(30);
		assertThat(registry.get("feed.age.seconds").tag("feed", "vehiclePositions").gauge().value()).isNaN();
	}
}
