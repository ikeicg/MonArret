package com.catchmystm.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.catchmystm.backend.dto.ApiResponse;
import com.catchmystm.backend.runtime.health.TripHealth;
import com.catchmystm.backend.service.TripHealthService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripHealthController {

    private final TripHealthService tripHealthService;

    // Nothing here touches the database, so no hop to boundedElastic.
    @GetMapping("/{tripId}/stops/{stopSequence}/health")
    public Mono<ApiResponse<TripHealth>> getTripHealth(
            @PathVariable String tripId,
            @PathVariable int stopSequence,
            ServerWebExchange exchange) {

        String version = tripHealthService.currentVersion();

        // no-cache: the client revalidates on every poll instead of reusing its copy.
        exchange.getResponse().getHeaders().setCacheControl("no-cache");
        if (exchange.checkNotModified(version)) {
            return Mono.empty();
        }

        return Mono.fromSupplier(() -> new ApiResponse<>(true, "Trip health",
                tripHealthService.getTripHealth(tripId, stopSequence, version)));
    }
}
