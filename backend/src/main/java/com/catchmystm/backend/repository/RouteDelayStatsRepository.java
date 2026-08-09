package com.catchmystm.backend.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.catchmystm.backend.entity.RouteDelayStats;

import reactor.core.publisher.Mono;

public interface RouteDelayStatsRepository extends ReactiveCrudRepository<RouteDelayStats, Long> {
	
	Mono<RouteDelayStats> findByRouteIdAndDirectionIdAndHourBucket(
			String routeId, Integer directionId, Short hourBucket
			);
}
