package com.monarret.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.web.reactive.function.client.WebClient;

import com.monarret.backend.runtime.ActiveTripStateStore;
import com.monarret.backend.runtime.LiveStateEngine;

@ExtendWith(MockitoExtension.class)
class LiveFeedHealthIndicatorTest {

	private static final Instant T0 = Instant.parse("2026-10-18T12:00:00Z");

	@Mock
	private ActiveTripStateStore store;

	@Mock
	private LiveStateEngine engine;

	private StmPoller poller;
	private LiveFeedHealthIndicator indicator;

	@BeforeEach
	void setUp() {
		poller = new StmPoller(WebClient.builder(), "http://localhost", "key");
		indicator = new LiveFeedHealthIndicator(poller, store, engine);
		when(store.getActiveTripCount()).thenReturn(2300);
		when(engine.feedVersion()).thenReturn("1760788800-1760788800");
	}

	@Test
	void beforeAnyPoll_reportsStartingWithoutAges() {
		Health health = indicator.health(T0);

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("feed", FeedHealth.STARTING)
				.doesNotContainKeys("vehiclePositionsAgeSeconds", "tripUpdatesAgeSeconds");
	}

	@Test
	void healthyFeed_reportsAgesStoreSizeAndFeedVersion() {
		poller.markVehiclePositionsReceived(T0);
		poller.markTripUpdatesReceived(T0.minusSeconds(5));

		Health health = indicator.health(T0.plusSeconds(20));

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("feed", FeedHealth.HEALTHY)
				.containsEntry("vehiclePositionsAgeSeconds", 20L)
				.containsEntry("tripUpdatesAgeSeconds", 25L)
				.containsEntry("activeTrips", 2300)
				.containsEntry("feedVersion", "1760788800-1760788800");
	}

	@Test
	void staleFeed_isReportedAsDownButTheAppStaysUp() {
		poller.markVehiclePositionsReceived(T0);
		poller.markTripUpdatesReceived(T0);

		Health health = indicator.health(T0.plus(StmPoller.FEED_STALE_AFTER).plusSeconds(1));

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("feed", FeedHealth.DOWN);
	}
}
