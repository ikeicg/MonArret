package com.catchmystm.backend.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.protobuf.ProtobufDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Slf4j
@Service
public class StmPoller {

    private final WebClient webClient;
    private final String apiKey;
    private final Duration transitFeedPollInterval = Duration.ofSeconds(20);

    private final Sinks.Many<FeedMessage> vehiclePositionSink = Sinks.many().multicast().directBestEffort();
    private final Sinks.Many<FeedMessage> tripUpdateSink = Sinks.many().multicast().directBestEffort();

    public StmPoller(
            WebClient.Builder webClientBuilder,
            @Value("${stm.api.url:https://api.stm.info/pub/od/gtfs-rt/ic/v2}") String stmApiUrl,
            @Value("${stm.api.key}") String apiKey) {
        
        this.apiKey = apiKey;
        this.webClient = webClientBuilder
            .baseUrl(stmApiUrl)
            .codecs(configurer -> configurer
                    .defaultCodecs()
                    .protobufDecoder(new ProtobufDecoder())) 
            .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startPolling() {
       
        Flux.interval(Duration.ZERO, transitFeedPollInterval)
                .flatMap(tick -> Mono.zip(
                        fetchGtfsFeed("/vehiclePositions").doOnNext(vehiclePositionSink::tryEmitNext),
                        fetchGtfsFeed("/tripUpdates").doOnNext(tripUpdateSink::tryEmitNext)
                ))
                .onErrorContinue((throwable, obj) -> 
                        log.error("Recovered from error during STM polling cycle: {}", throwable.getMessage())
                )
                .subscribe();
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

    public Flux<FeedMessage> getVehiclePositionsPublisher() {
        return vehiclePositionSink.asFlux();
    }

    public Flux<FeedMessage> getTripUpdatesPublisher() {
        return tripUpdateSink.asFlux();
    }
}