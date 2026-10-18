package com.monarret.backend.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.monarret.backend.service.StmPoller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class LiveStateEngineBackpressureTest {

	@Mock
	private StmPoller poller;

	@Mock
	private ActiveTripStateStore store;

	private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
	private final Sinks.Many<FeedMessage> feed = Sinks.many().multicast().directBestEffort();
	private LiveStateEngine engine;
	private Disposable subscription;

	@BeforeEach
	void setUp() {
		when(poller.getTripUpdatesPublisher()).thenReturn(Flux.empty());
		when(poller.getVehiclePositionsPublisher()).thenReturn(Flux.empty());
		Clock clock = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneId.of("America/Montreal"));
		engine = new LiveStateEngine(poller, store, new GeometricOperationsService(), clock, meters);
	}

	@AfterEach
	void tearDown() {
		if (subscription != null) {
			subscription.dispose();
		}
	}

	private static FeedMessage message(int id) {
		return FeedMessage.newBuilder()
				.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(id))
				.build();
	}

	private static int id(FeedMessage message) {
		return (int) message.getHeader().getTimestamp();
	}

	@Test
	void latestWins_keepsOnlyTheNewestMessageWhileTheConsumerIsBusy() {
		AtomicInteger dropped = new AtomicInteger();
		Sinks.Many<Integer> source = Sinks.many().multicast().directBestEffort();

		// the consumer subscribes but asks for nothing until all five messages have been sent
		StepVerifier.create(LiveStateEngine.latestWins(source.asFlux(), dropped::incrementAndGet), 0)
				.expectSubscription()
				.then(() -> {
					for (int i = 1; i <= 5; i++) {
						source.tryEmitNext(i);
					}
				})
				.thenRequest(1)
				.expectNext(5)
				.then(source::tryEmitComplete)
				.expectComplete()
				.verify(Duration.ofSeconds(2));

		assertThat(dropped).hasValue(4);
	}

	@Test
	void aSlowIngest_skipsTheMessagesInBetweenAndCountsThem() throws InterruptedException {
		Counter dropped = meters.counter("test.dropped");
		List<Integer> ingested = new CopyOnWriteArrayList<>();
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch lastIngested = new CountDownLatch(1);

		subscription = engine.subscribeTo(feed.asFlux(), "test", dropped, message -> {
			ingested.add(id(message));
			if (id(message) == 1) {
				firstStarted.countDown();
				await(release);
			}
			if (id(message) == 4) {
				lastIngested.countDown();
			}
		});

		feed.tryEmitNext(message(1));
		await(firstStarted);
		feed.tryEmitNext(message(2));
		feed.tryEmitNext(message(3));
		feed.tryEmitNext(message(4));
		release.countDown();
		await(lastIngested);

		assertThat(ingested).containsExactly(1, 4);
		assertThat(dropped.count()).isEqualTo(2);
	}

	@Test
	void aFailureWhileIngestingOneMessage_doesNotStopLaterMessages() throws InterruptedException {
		Counter dropped = meters.counter("test.dropped");
		CountDownLatch firstSeen = new CountDownLatch(1);
		CountDownLatch secondSeen = new CountDownLatch(1);

		subscription = engine.subscribeTo(feed.asFlux(), "test", dropped, message -> {
			if (id(message) == 1) {
				firstSeen.countDown();
				throw new IllegalStateException("bad message");
			}
			secondSeen.countDown();
		});

		feed.tryEmitNext(message(1));
		await(firstSeen);
		feed.tryEmitNext(message(2));

		assertThat(secondSeen.await(2, TimeUnit.SECONDS)).isTrue();
	}

	private static void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
