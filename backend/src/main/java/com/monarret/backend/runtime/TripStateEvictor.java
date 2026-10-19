package com.monarret.backend.runtime;

import java.time.Duration;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.monarret.backend.service.FeedHealth;
import com.monarret.backend.service.StmPoller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripStateEvictor {

	// A state unseen for MAX_AGE is removed by the next sweep, so a bus that left the feed is gone within
	// MAX_AGE plus the sweep interval: 5 minutes.
	static final Duration MAX_AGE = Duration.ofMinutes(4);

	private final ActiveTripStateStore store;
	private final StmPoller poller;

	@Scheduled(fixedDelay = 60 * 1000)
	public void sweep() {
		sweep(Instant.now());
	}

	// Skipped unless the feed is healthy: during an outage every state looks stale.
	int sweep(Instant now) {
		if (poller.getFeedHealth(now) != FeedHealth.HEALTHY) return 0;

		int evicted = store.evictStale(now.minus(MAX_AGE));
		if (evicted > 0) {
			log.info("Evicted {} stale trip states", evicted);
		}
		return evicted;
	}
}
