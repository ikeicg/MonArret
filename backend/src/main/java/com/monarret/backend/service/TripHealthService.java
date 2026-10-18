package com.monarret.backend.service;

import java.time.Clock;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.monarret.backend.config.HealthCacheConfiguration;
import com.monarret.backend.runtime.ActiveTripStateStore;
import com.monarret.backend.runtime.LiveStateEngine;
import com.monarret.backend.runtime.health.TripHealth;
import com.monarret.backend.runtime.health.TripHealthAssembler;

import lombok.RequiredArgsConstructor;

// Read-only: store lookups and in-memory computation, no database.
@Service
@RequiredArgsConstructor
public class TripHealthService {

    private final ActiveTripStateStore store;
    private final StmPoller poller;
    private final LiveStateEngine liveStateEngine;
    private final Clock montrealClock;

    // Changes when the ingested live data does, and when the feed goes down or recovers, which can happen
    // with no new message arriving.
    public String currentVersion() {
        return liveStateEngine.feedVersion() + "-" + poller.getFeedHealth();
    }

    // The version is an argument, not read here, so the cache key and the ETag come from the same value.
    @Cacheable(cacheManager = HealthCacheConfiguration.MANAGER, cacheNames = HealthCacheConfiguration.TRIP_HEALTH_CACHE, sync = true,
            key = "#tripId + ':' + #stopSequence + ':' + #version")
    public TripHealth getTripHealth(String tripId, int stopSequence, String version) {
        FeedHealth feedHealth = poller.getFeedHealth();

        return store.snapshotTrip(tripId)
                .map(trip -> TripHealthAssembler.assemble(trip, stopSequence, montrealClock.instant(), feedHealth))
                .orElseGet(() -> TripHealthAssembler.assembleWithoutState(tripId, stopSequence, feedHealth));
    }
}
