package com.monarret.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class StmPollerFeedHealthTest {

	private static final Instant T0 = Instant.parse("2026-10-03T12:00:00Z");

	private StmPoller poller;

	@BeforeEach
	void setUp() {
		poller = new StmPoller(WebClient.builder(), "http://localhost", "key");
	}

	@Test
	void beforeAnyPoll_isStarting() {
		assertThat(poller.getFeedHealth(T0)).isEqualTo(FeedHealth.STARTING);
	}

	@Test
	void bothFeedsRecent_isHealthy() {
		poller.markVehiclePositionsReceived(T0);
		poller.markTripUpdatesReceived(T0);

		assertThat(poller.getFeedHealth(T0.plusSeconds(20))).isEqualTo(FeedHealth.HEALTHY);
	}

	@Test
	void exactlyAtTheStaleLimit_isStillHealthy() {
		poller.markVehiclePositionsReceived(T0);
		poller.markTripUpdatesReceived(T0);

		assertThat(poller.getFeedHealth(T0.plus(StmPoller.FEED_STALE_AFTER))).isEqualTo(FeedHealth.HEALTHY);
	}

	@Test
	void pastTheStaleLimit_isDown() {
		poller.markVehiclePositionsReceived(T0);
		poller.markTripUpdatesReceived(T0);

		assertThat(poller.getFeedHealth(T0.plus(StmPoller.FEED_STALE_AFTER).plusSeconds(1)))
				.isEqualTo(FeedHealth.DOWN);
	}

	@Test
	void oneFeedStale_isDown() {
		Instant now = T0.plusSeconds(120);
		poller.markVehiclePositionsReceived(now);
		poller.markTripUpdatesReceived(T0);

		assertThat(poller.getFeedHealth(now)).isEqualTo(FeedHealth.DOWN);
	}

	@Test
	void onlyOneFeedEverReceived_isDown() {
		poller.markVehiclePositionsReceived(T0);

		assertThat(poller.getFeedHealth(T0)).isEqualTo(FeedHealth.DOWN);
	}

	@Test
	void recoversToHealthy_afterAnOutage() {
		poller.markVehiclePositionsReceived(T0);
		poller.markTripUpdatesReceived(T0);
		Instant later = T0.plusSeconds(300);
		assertThat(poller.getFeedHealth(later)).isEqualTo(FeedHealth.DOWN);

		poller.markVehiclePositionsReceived(later);
		poller.markTripUpdatesReceived(later);

		assertThat(poller.getFeedHealth(later)).isEqualTo(FeedHealth.HEALTHY);
	}
}
