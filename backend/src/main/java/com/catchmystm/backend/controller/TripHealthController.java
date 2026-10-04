package com.catchmystm.backend.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import com.catchmystm.backend.dto.ApiResponse;
import com.catchmystm.backend.runtime.health.TripHealth;
import com.catchmystm.backend.service.TripHealthService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripHealthController {

    private final TripHealthService tripHealthService;

    // Nothing here touches the database, so no hop to boundedElastic.
    @GetMapping("/{tripId}/stops/{stopSequence}/health")
    public ApiResponse<TripHealth> getTripHealth(
            @PathVariable String tripId,
            @PathVariable int stopSequence,
            WebRequest request,
            HttpServletResponse response) {

        String version = tripHealthService.currentVersion();

        // no-cache: the client revalidates on every poll instead of reusing its copy.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        if (request.checkNotModified(version)) {
            return null;
        }

        return new ApiResponse<>(true, "Trip health", tripHealthService.getTripHealth(tripId, stopSequence, version));
    }
}
