package com.monarret.backend.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.monarret.backend.config.HealthCacheConfiguration;
import com.monarret.backend.runtime.ActiveTripStateStore;
import com.monarret.backend.runtime.LiveStateEngine;

// Goes through the real caching proxy; calling the service directly would skip @Cacheable. The cache is
// shared across the tests in this class, so each test uses its own trip id.
@SpringJUnitConfig(TripHealthCachingTest.Config.class)
class TripHealthCachingTest {

	@Configuration
	@EnableCaching
	@Import(HealthCacheConfiguration.class)
	static class Config {

		@Bean
		ActiveTripStateStore store() {
			return mock(ActiveTripStateStore.class);
		}

		@Bean
		StmPoller poller() {
			StmPoller poller = mock(StmPoller.class);
			when(poller.getFeedHealth()).thenReturn(FeedHealth.HEALTHY);
			return poller;
		}

		@Bean
		LiveStateEngine liveStateEngine() {
			return mock(LiveStateEngine.class);
		}

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneId.of("America/Montreal"));
		}

		@Bean
		TripHealthService tripHealthService(ActiveTripStateStore store, StmPoller poller, LiveStateEngine engine,
				Clock clock) {
			return new TripHealthService(store, poller, engine, clock);
		}
	}

	@Autowired
	private TripHealthService service;

	@Autowired
	private ActiveTripStateStore store;

	@Test
	void sameTripStopAndVersion_isBuiltOnce() {
		service.getTripHealth("cache-1", 4, "v1");
		service.getTripHealth("cache-1", 4, "v1");

		verify(store, times(1)).snapshotTrip("cache-1");
	}

	@Test
	void newVersion_missesTheCache() {
		service.getTripHealth("cache-2", 4, "v1");
		service.getTripHealth("cache-2", 4, "v2");

		verify(store, times(2)).snapshotTrip("cache-2");
	}

	@Test
	void differentStops_areCachedSeparately() {
		service.getTripHealth("cache-3", 4, "v1");
		service.getTripHealth("cache-3", 5, "v1");
		service.getTripHealth("cache-3", 4, "v1");

		verify(store, times(2)).snapshotTrip("cache-3");
	}

	@Test
	void manyRequestsForTheSameMissingEntry_buildItOnlyOnce() throws Exception {
		int requests = 20;
		when(store.snapshotTrip("cache-stampede")).thenAnswer(call -> {
			Thread.sleep(200);
			return Optional.empty();
		});
		ExecutorService pool = Executors.newFixedThreadPool(requests);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<?>> calls = new ArrayList<>();
		for (int i = 0; i < requests; i++) {
			calls.add(pool.submit(() -> {
				go.await();
				return service.getTripHealth("cache-stampede", 4, "v1");
			}));
		}

		go.countDown();
		for (Future<?> call : calls) {
			call.get();
		}
		pool.shutdown();

		verify(store, times(1)).snapshotTrip("cache-stampede");
	}
}
