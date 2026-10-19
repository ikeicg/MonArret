package com.monarret.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.monarret.backend.config.PollProperties;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Responses that arrive but are not a usable feed: the poller delivers nothing and keeps polling. */
class StmPollerBrokenResponseTest {

	private static final Duration INTERVAL = Duration.ofSeconds(20);

	private StmPoller poller;

	@AfterEach
	void stopPolling() {
		if (poller != null) {
			poller.stopPolling();
		}
	}

	private static byte[] aRealMessage() {
		return FeedMessage.newBuilder()
				.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(1_000))
				.addEntity(FeedEntity.newBuilder().setId("1").setTripUpdate(TripUpdate.newBuilder()
						.setTrip(TripDescriptor.newBuilder().setTripId("trip-1").setStartDate("20261018"))))
				.build().toByteArray();
	}

	private static Mono<ClientResponse> ok(byte[] body) {
		return Mono.just(ClientResponse.create(HttpStatus.OK, ExchangeStrategies.withDefaults())
				.header("Content-Type", MediaType.APPLICATION_PROTOBUF_VALUE)
				.body(Flux.just(new DefaultDataBufferFactory().wrap(body)))
				.build());
	}

	private StmPoller pollerAnswering(ExchangeFunction fake) {
		poller = new StmPoller(WebClient.builder().exchangeFunction(fake), "http://stm.test", "key",
				new PollProperties(INTERVAL));
		return poller;
	}

	// Polls for 65 s of virtual time (ticks at 0, 20, 40 and 60 s) and expects no message to come out.
	private void expectNothingDelivered(StmPoller poller) {
		StepVerifier.withVirtualTime(() -> poller.pollingLoop("/tripUpdates", INTERVAL, message -> { }))
				.expectSubscription()
				.expectNoEvent(Duration.ofSeconds(65))
				.thenCancel()
				.verify(Duration.ofSeconds(3));
	}

	@Test
	void aBodyThatIsNotProtobuf_isDroppedAndPollingContinues() {
		AtomicInteger requests = new AtomicInteger();
		StmPoller poller = pollerAnswering(request -> {
			requests.incrementAndGet();
			return ok("<html>502 Bad Gateway</html>".getBytes(StandardCharsets.UTF_8));
		});

		expectNothingDelivered(poller);

		assertThat(requests).hasValue(4);
	}

	@Test
	void anEmptyBody_isNotAFeed() {
		AtomicInteger requests = new AtomicInteger();
		StmPoller poller = pollerAnswering(request -> {
			requests.incrementAndGet();
			return ok(new byte[0]);
		});

		expectNothingDelivered(poller);

		assertThat(requests).hasValue(4);
	}

	@Test
	void aMessageCutOffHalfway_isDropped() {
		byte[] whole = aRealMessage();
		byte[] truncated = Arrays.copyOf(whole, whole.length - 8);
		StmPoller poller = pollerAnswering(request -> ok(truncated));

		expectNothingDelivered(poller);
	}

	@Test
	void aRequestThatNeverAnswers_timesOutAndTheNextTickTriesAgain() {
		AtomicInteger requests = new AtomicInteger();
		StmPoller poller = pollerAnswering(request -> {
			requests.incrementAndGet();
			return Mono.never();
		});

		expectNothingDelivered(poller);

		assertThat(requests).hasValue(4);
	}

	@Test
	void afterABrokenResponse_theNextGoodOneIsDelivered() {
		AtomicInteger requests = new AtomicInteger();
		StmPoller poller = pollerAnswering(request ->
				requests.incrementAndGet() == 1 ? ok("not a feed".getBytes(StandardCharsets.UTF_8)) : ok(aRealMessage()));

		StepVerifier.withVirtualTime(() -> poller.pollingLoop("/tripUpdates", INTERVAL, message -> { }))
				.expectSubscription()
				.expectNoEvent(Duration.ofSeconds(19))
				.thenAwait(Duration.ofSeconds(2))
				.assertNext(message -> assertThat(message.getEntityCount()).isEqualTo(1))
				.thenCancel()
				.verify(Duration.ofSeconds(3));
	}

	@Test
	void aValidMessageWithNoEntities_isStillAFeedAndIsDelivered() {
		byte[] headerOnly = FeedMessage.newBuilder()
				.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(1_000))
				.build().toByteArray();
		StmPoller poller = pollerAnswering(request -> ok(headerOnly));

		StepVerifier.withVirtualTime(() -> poller.pollingLoop("/tripUpdates", INTERVAL, message -> { }))
				.expectSubscription()
				.assertNext(message -> assertThat(message.getEntityCount()).isZero())
				.thenCancel()
				.verify(Duration.ofSeconds(3));
	}
}
