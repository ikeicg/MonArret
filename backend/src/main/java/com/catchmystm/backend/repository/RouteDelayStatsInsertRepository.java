package com.catchmystm.backend.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import com.catchmystm.backend.entity.RouteDelayStats;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class RouteDelayStatsInsertRepository {

    private final DatabaseClient databaseClient;
    
    public Mono<Boolean> insertIfAbsent(RouteDelayStats bucket) {

        String sql = """
            INSERT INTO route_delay_stats (
                route_id,
                direction_id,
                hour_bucket,
                sample_count,
                mean_delay,
                m2,
                last_reset_date,
                version
            )
            VALUES (
                :routeId,
                :directionId,
                :hourBucket,
                :sampleCount,
                :meanDelay,
                :m2,
                :lastResetDate,
                0
            )
            ON CONFLICT (route_id, direction_id, hour_bucket)
            DO NOTHING
            """;

        return databaseClient.sql(sql)
                .bind("routeId", bucket.getRouteId())
                .bind("directionId", bucket.getDirectionId())
                .bind("hourBucket", bucket.getHourBucket())
                .bind("sampleCount", bucket.getSampleCount())
                .bind("meanDelay", bucket.getMeanDelay())
                .bind("m2", bucket.getM2())
                .bind("lastResetDate", bucket.getLastResetDate())
                .fetch()
                .rowsUpdated()
                .map(rows -> rows == 1);
    }
}