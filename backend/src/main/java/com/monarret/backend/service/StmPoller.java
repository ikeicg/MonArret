package com.monarret.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.protobuf.ProtobufDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

@Slf4j
@Service
public class StmPoller {

    private final WebClient webClient;
    private final String apiKey;
    private final Duration transitFeedPollInterval = Duration.ofSeconds(20);

    // WebClient's default cap is 256 KB, below the size of the tripUpdates feed.
    static final int MAX_FEED_BYTES = 16 * 1024 * 1024;

    // Three missed poll cycles
    static final Duration FEED_STALE_AFTER = Duration.ofSeconds(60);
    private volatile Instant lastVehiclePositionsSuccess;
    private volatile Instant lastTripUpdatesSuccess;

    private final Disposable.Composite polling = Disposables.composite();

    private final Sinks.Many<FeedMessage> vehiclePositionSink = Sinks.many().multicast().directBestEffort();
    private final Sinks.Many<FeedMessage> tripUpdateSink = Sinks.many().multicast().directBestEffort();

    public StmPoller(
            WebClient.Builder webClientBuilder,
            @Value("${stm.api.url:https://api.stm.info/pub/od/gtfs-rt/ic/v2}") String stmApiUrl,
            @Value("${stm.api.key}") String apiKey) {
        
        this.apiKey = apiKey;
        this.webClient = webClientBuilder
            .baseUrl(stmApiUrl)
            .codecs(configurer -> {
                configurer.defaultCodecs().maxInMemorySize(MAX_FEED_BYTES);
                configurer.defaultCodecs().protobufDecoder(new ProtobufDecoder());
            })
            .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startPolling() {
        poll("/vehiclePositions", vehiclePositionSink, this::markVehiclePositionsReceived);
        poll("/tripUpdates", tripUpdateSink, this::markTripUpdatesReceived);
    }

    @PreDestroy
    public void stopPolling() {
        polling.dispose();
    }

    // One loop per feed, so a failing feed cannot cancel or delay the other.
    private void poll(String endpoint, Sinks.Many<FeedMessage> sink, Consumer<Instant> markReceived) {
        polling.add(pollingLoop(endpoint, transitFeedPollInterval).subscribe(feed -> {
            sink.tryEmitNext(feed);
            markReceived.accept(Instant.now());
        }));
    }

    // At most one fetch at a time: a tick that arrives while the previous fetch is still running is skipped.
    Flux<FeedMessage> pollingLoop(String endpoint, Duration interval) {
        return selfHealing(endpoint, () -> Flux.interval(Duration.ZERO, interval)
                .onBackpressureDrop(tick -> log.warn("Skipping a poll of {}: the previous one is still running", endpoint))
                .flatMap(tick -> fetchGtfsFeed(endpoint), 1));
    }

    // Failed fetches are already absorbed in fetchGtfsFeed. This restarts the loop if the stream itself ever errors.
    static <T> Flux<T> selfHealing(String name, Supplier<Flux<T>> source) {
        return Flux.defer(source).retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(30))
                .transientErrors(true)
                .doBeforeRetry(signal -> log.error("Polling of {} stopped ({}), restarting",
                        name, signal.failure().toString())));
    }

    private Mono<FeedMessage> fetchGtfsFeed(String endpoint) {
        return webClient.get()
                .uri(endpoint)
                .header("apiKey", apiKey)
                .header(HttpHeaders.ACCEPT, "application/x-protobuf")
                .retrieve()
                .bodyToMono(byte[].class)
                .map(bytes -> {
                	try {
                		 return FeedMessage.parseFrom(bytes);
                	}
                	catch (Exception e) {
                		throw new RuntimeException("Invalid GTFS-RT protobuf", e);
                	}
                }) 
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(ex -> {
                    log.error("Failed to fetch STM GTFS data from {}: {}", endpoint, ex.getMessage());
                    return Mono.empty(); 
                });
    }

    void markVehiclePositionsReceived(Instant at) {
        lastVehiclePositionsSuccess = at;
    }

    void markTripUpdatesReceived(Instant at) {
        lastTripUpdatesSuccess = at;
    }

    public Instant getLastVehiclePositionsSuccess() {
        return lastVehiclePositionsSuccess;
    }

    public Instant getLastTripUpdatesSuccess() {
        return lastTripUpdatesSuccess;
    }

    public FeedHealth getFeedHealth() {
        return getFeedHealth(Instant.now());
    }

    public FeedHealth getFeedHealth(Instant now) {
        Instant vehicles = lastVehiclePositionsSuccess;
        Instant updates = lastTripUpdatesSuccess;
        if (vehicles == null && updates == null) return FeedHealth.STARTING;
        if (vehicles == null || updates == null) return FeedHealth.DOWN;

        Instant cutoff = now.minus(FEED_STALE_AFTER);
        return vehicles.isBefore(cutoff) || updates.isBefore(cutoff) ? FeedHealth.DOWN : FeedHealth.HEALTHY;
    }

    public Flux<FeedMessage> getVehiclePositionsPublisher() {
        return vehiclePositionSink.asFlux();
    }

    public Flux<FeedMessage> getTripUpdatesPublisher() {
        return tripUpdateSink.asFlux();
    }
}