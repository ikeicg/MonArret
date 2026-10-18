package com.monarret.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** The STM calls are replaced by a fake HTTP layer, so these tests need no network. */
class StmPollerFeedIndependenceTest {

	private static final Duration SLOW_FEED_DELAY = Duration.ofMillis(300);

	private StmPoller poller;

	@AfterEach
	void stopPolling() {
		if (poller != null) {
			poller.stopPolling();
		}
	}

	private StmPoller newPoller(ExchangeFunction fake) {
		poller = new StmPoller(WebClient.builder().exchangeFunction(fake), "http://stm.test", "key");
		return poller;
	}

	private static ClientResponse validFeed() {
		byte[] bytes = FeedMessage.newBuilder()
				.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(1_000))
				.build().toByteArray();
		return ClientResponse.create(HttpStatus.OK, ExchangeStrategies.withDefaults())
				.header("Content-Type", MediaType.APPLICATION_PROTOBUF_VALUE)
				.body(Flux.just(new DefaultDataBufferFactory().wrap(bytes)))
				.build();
	}

	private static ClientResponse serverError() {
		return ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR, ExchangeStrategies.withDefaults()).build();
	}

	// tripUpdates fails at once, vehiclePositions answers after SLOW_FEED_DELAY
	private StmPoller pollerWhereTripUpdatesFailFast() {
		return newPoller(request -> request.url().getPath().endsWith("/tripUpdates")
				? Mono.just(serverError())
				: Mono.delay(SLOW_FEED_DELAY).thenReturn(validFeed()));
	}

	// Control: with both feeds healthy the same setup delivers, so the test below fails only because of the bug.
	@Test
	void whenBothFeedsAreHealthy_vehiclePositionsArrive() {
		StmPoller poller = newPoller(request -> Mono.delay(SLOW_FEED_DELAY).thenReturn(validFeed()));

		StepVerifier.create(poller.getVehiclePositionsPublisher().take(1))
				.then(poller::startPolling)
				.expectNextCount(1)
				.expectComplete()
				.verify(Duration.ofSeconds(3));
	}

	@Test
	void aFailingTripUpdatesFeed_doesNotStopVehiclePositionsFromArriving() {
		StmPoller poller = pollerWhereTripUpdatesFailFast();

		StepVerifier.create(poller.getVehiclePositionsPublisher().take(1))
				.then(poller::startPolling)
				.expectNextCount(1)
				.expectComplete()
				.verify(Duration.ofSeconds(3));
	}

	@Test
	void aTickThatArrivesWhileAFetchIsRunning_isSkipped() {
		AtomicInteger requests = new AtomicInteger();
		StmPoller poller = newPoller(request -> {
			requests.incrementAndGet();
			return Mono.delay(Duration.ofSeconds(5)).thenReturn(validFeed());
		});

		// ticks every 2 s, each fetch takes 5 s: fetches start at 0 s, 6 s and 12 s instead of at every tick
		StepVerifier.withVirtualTime(() -> poller.pollingLoop("/tripUpdates", Duration.ofSeconds(2)))
				.expectSubscription()
				.thenAwait(Duration.ofSeconds(5))
				.expectNextCount(1)
				.thenAwait(Duration.ofSeconds(6))
				.expectNextCount(1)
				.thenAwait(Duration.ofMillis(1500))
				.then(() -> assertThat(requests).hasValue(3))
				.thenCancel()
				.verify(Duration.ofSeconds(3));
	}

	@Test
	void whenTheStreamItselfErrors_theLoopRestartsWithBackoff() {
		AtomicInteger attempts = new AtomicInteger();
		Supplier<Flux<String>> failsTwiceThenWorks = () -> Flux.defer(() -> attempts.incrementAndGet() <= 2
				? Flux.<String>error(new IllegalStateException("boom"))
				: Flux.just("recovered"));

		StepVerifier.withVirtualTime(() -> StmPoller.selfHealing("test", failsTwiceThenWorks))
				.expectSubscription()
				.thenAwait(Duration.ofSeconds(10))
				.expectNext("recovered")
				.expectComplete()
				.verify(Duration.ofSeconds(3));

		assertThat(attempts).hasValue(3);
	}
}
