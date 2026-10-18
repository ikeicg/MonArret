package com.monarret.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.monarret.backend.dto.StopTimeDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartureService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Cacheable("daily-departures")
    public List<StopTimeDto> findDeparturesForToday(
            String routeId,
            Integer directionId,
            String todayAsString) {

        log.debug("Departures cache miss: route {}, direction {}, {}", routeId, directionId, todayAsString);
        LocalDate today = LocalDate.parse(todayAsString);

        String sql = """
                WITH candidate_service_dates AS (

                    -- Yesterday's service dates
                    SELECT
                        sd.service_id,
                        -86400 AS day_offset,
                        sd.valid_date
                    FROM gtfs_static.service_dates sd
                    WHERE sd.valid_date = :yesterday

                    UNION ALL

                    -- Today's service dates
                    SELECT
                        sd.service_id,
                        0 AS day_offset,
                        sd.valid_date
                    FROM gtfs_static.service_dates sd
                    WHERE sd.valid_date = :today

                    UNION ALL

                    -- Tomorrow's service dates
                    SELECT
                        sd.service_id,
                        86400 AS day_offset,
                        sd.valid_date
                    FROM gtfs_static.service_dates sd
                    WHERE sd.valid_date = :tomorrow
                )

                SELECT
                    st.trip_id,
                    st.stop_id,
                    st.stop_sequence,
                    st.departure_time,
                    s.stop_name,
                    csd.valid_date

                FROM gtfs_static.stop_times st

                JOIN gtfs_static.trips t
                    ON t.trip_id = st.trip_id

                JOIN gtfs_static.stops s
                    ON s.stop_id = st.stop_id

                JOIN candidate_service_dates csd
                    ON csd.service_id = t.service_id

                WHERE t.route_id = :route_id
                  AND t.direction_id = :direction_id

                  -- Future departures for today up half of the next day
                  AND (st.departure_time + csd.day_offset) >= 0
                  AND (st.departure_time + csd.day_offset) <= 129600

                ORDER BY (st.departure_time + csd.day_offset) ASC
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("yesterday", today.minusDays(1))
                .addValue("today", today)
                .addValue("tomorrow", today.plusDays(1))
                .addValue("route_id", routeId)
                .addValue("direction_id", directionId);

        List<StopTimeDto> result = jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> {

                    LocalDate validDate =
                            rs.getObject("valid_date", LocalDate.class);

                    int secondsOfDay = rs.getInt("departure_time");

                    // Handle GTFS extended clock
                    if (secondsOfDay >= 86400) {
                        validDate = validDate.plusDays(1);
                        secondsOfDay -= 86400;
                    }

                    LocalDateTime departureTime = validDate.atTime(LocalTime.ofSecondOfDay(secondsOfDay));

                    return new StopTimeDto(
                            rs.getString("trip_id"),
                            rs.getString("stop_id"),
                            rs.getString("stop_name"),
                            departureTime,
                            rs.getInt("stop_sequence")
                    );
                });
        
        return result;


    }
}
