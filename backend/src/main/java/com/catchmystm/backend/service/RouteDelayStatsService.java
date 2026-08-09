package com.catchmystm.backend.service;

import java.time.Clock;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.catchmystm.backend.entity.RouteDelayStats;
import com.catchmystm.backend.repository.RouteDelayStatsInsertRepository;
import com.catchmystm.backend.repository.RouteDelayStatsRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RouteDelayStatsService {

    private final Clock montrealClock;
    private final RouteDelayStatsRepository routeDelayStatsRepository;
    private final RouteDelayStatsInsertRepository insertRepository;

    private static final int MAX_ATTEMPTS = 10;

    public Mono<RouteDelayStats> recordDelay(
            String routeId,
            Integer directionId,
            Double delayInSeconds) {

        short hourBucket =
                (short) LocalTime.now(montrealClock).getHour();

        LocalDate today =
                LocalDate.now(montrealClock);

        return updateStats(
                routeId,
                directionId,
                hourBucket,
                today,
                delayInSeconds,
                0
        );
    }

    private Mono<RouteDelayStats> updateStats(
            String routeId,
            Integer directionId,
            short hourBucket,
            LocalDate today,
            Double delayInSeconds,
            int attempt) {

        if (attempt >= MAX_ATTEMPTS) {
            return Mono.error(
                    new IllegalStateException(
                            "Maximum retry attempts exceeded"
                    )
            );
        }

        return routeDelayStatsRepository
                .findByRouteIdAndDirectionIdAndHourBucket(
                        routeId,
                        directionId,
                        hourBucket
                )
                .flatMap(bucket ->
                        updateExistingBucket(
                                bucket,
                                today,
                                delayInSeconds
                        )
                )
                .switchIfEmpty(
                        createOrAcquireBucket(
                                routeId,
                                directionId,
                                hourBucket,
                                today,
                                delayInSeconds,
                                attempt
                        )
                )
                .onErrorResume(
                        OptimisticLockingFailureException.class,
                        error -> updateStats(
                                routeId,
                                directionId,
                                hourBucket,
                                today,
                                delayInSeconds,
                                attempt + 1
                        )
                );
    }

    private Mono<RouteDelayStats> updateExistingBucket(
            RouteDelayStats bucket,
            LocalDate today,
            Double delayInSeconds) {

        if (!today.equals(bucket.getLastResetDate())) {
            bucket.resetState(today);
        }

        bucket.addObservation(delayInSeconds);

        return routeDelayStatsRepository.save(bucket);
    }

    private Mono<RouteDelayStats> createOrAcquireBucket(
            String routeId,
            Integer directionId,
            short hourBucket,
            LocalDate today,
            Double delayInSeconds,
            int attempt) {

        RouteDelayStats newBucket = RouteDelayStats.builder()
                .routeId(routeId)
                .directionId(directionId)
                .hourBucket(hourBucket)
                .lastResetDate(today)
                .build();

        newBucket.addObservation(delayInSeconds);

        return insertRepository
                .insertIfAbsent(newBucket)
                .flatMap(inserted -> {

                	if (inserted) {
                        return routeDelayStatsRepository
                                .findByRouteIdAndDirectionIdAndHourBucket(
                                        routeId,
                                        directionId,
                                        hourBucket
                                );
                    }

                    // Bucket not inserted (already created) then go through an update path
                    
                    return updateStats(
                            routeId,
                            directionId,
                            hourBucket,
                            today,
                            delayInSeconds,
                            attempt + 1
                    );
                });
    }

    public Mono<RouteDelayStats> getPreviousHourBucket(
            String routeId,
            Integer directionId) {

        short previousHour =
                (short) LocalTime.now(montrealClock)
                        .minusHours(1)
                        .getHour();

        return routeDelayStatsRepository
                .findByRouteIdAndDirectionIdAndHourBucket(
                        routeId,
                        directionId,
                        previousHour
                );
    }
}