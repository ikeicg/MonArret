package com.catchmystm.backend.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.catchmystm.backend.service.FeedHealth;
import com.catchmystm.backend.service.StmPoller;

class TripStateEvictorTest {

	private static final LocalDate DAY = LocalDate.of(2026, 10, 3);
	private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

	private ActiveTripStateFactory factory;
	private ActiveTripStateStore store;
	private StmPoller poller;
	private TripStateEvictor evictor;

	@BeforeEach
	void setUp() {
		factory = mock(ActiveTripStateFactory.class);
		store = new ActiveTripStateStore(factory);
		poller = mock(StmPoller.class);
		evictor = new TripStateEvictor(store, poller);
		when(factory.create("old", DAY)).thenAnswer(i -> state("old"));
		when(factory.create("fresh", DAY)).thenAnswer(i -> state("fresh"));
	}

	@Test
	void store_evictsOnlyStatesOlderThanTheCutoff() {
		seed("old", NOW.minusSeconds(600));
		seed("fresh", NOW.minusSeconds(60));

		int evicted = store.evictStale(NOW.minus(TripStateEvictor.MAX_AGE));

		assertThat(evicted).isEqualTo(1);
		assertThat(store.fetchTripState("old")).isEmpty();
		assertThat(store.fetchTripState("fresh")).isPresent();
	}

	@Test
	void store_stateExactlyAtTheCutoff_isKept() {
		Instant cutoff = NOW.minus(TripStateEvictor.MAX_AGE);
		seed("old", cutoff);

		assertThat(store.evictStale(cutoff)).isZero();
		assertThat(store.fetchTripState("old")).isPresent();
	}

	@Test
	void sweep_whenHealthy_evictsStaleStates() {
		when(poller.getFeedHealth(NOW)).thenReturn(FeedHealth.HEALTHY);
		seed("old", NOW.minusSeconds(600));
		seed("fresh", NOW.minusSeconds(60));

		assertThat(evictor.sweep(NOW)).isEqualTo(1);
		assertThat(store.getActiveTripCount()).isEqualTo(1);
	}

	@Test
	void sweep_whenFeedDown_keepsEverything() {
		when(poller.getFeedHealth(NOW)).thenReturn(FeedHealth.DOWN);
		seed("old", NOW.minusSeconds(600));

		assertThat(evictor.sweep(NOW)).isZero();
		assertThat(store.getActiveTripCount()).isEqualTo(1);
	}

	@Test
	void sweep_whenStarting_keepsEverything() {
		when(poller.getFeedHealth(NOW)).thenReturn(FeedHealth.STARTING);
		seed("old", NOW.minusSeconds(600));

		assertThat(evictor.sweep(NOW)).isZero();
		assertThat(store.getActiveTripCount()).isEqualTo(1);
	}

	private void seed(String tripId, Instant lastSeen) {
		store.fetchOrCreateTripState(tripId, DAY).setLastSeenInFeed(lastSeen);
	}

	private ActiveTripState state(String tripId) {
		ActiveTripState s = new ActiveTripState();
		s.setTripId(tripId);
		s.setServiceDate(DAY);
		return s;
	}
}
