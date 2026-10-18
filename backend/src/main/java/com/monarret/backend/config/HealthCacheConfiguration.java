package com.monarret.backend.config;

import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

// In-memory cache for the Trip Health responses. The feed version is part of the key, so a new feed
// cycle never hits an old entry; the expiry and size cap only clear the dead ones.
@Configuration
public class HealthCacheConfiguration {

    public static final String MANAGER = "healthCacheManager";
    public static final String TRIP_HEALTH_CACHE = "trip-health";

    static final Duration EXPIRE_AFTER_WRITE = Duration.ofSeconds(40);
    static final long MAX_ENTRIES = 5_000;

    @Bean(MANAGER)
    public CacheManager healthCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(TRIP_HEALTH_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(EXPIRE_AFTER_WRITE)
                .maximumSize(MAX_ENTRIES));
        return manager;
    }
}
