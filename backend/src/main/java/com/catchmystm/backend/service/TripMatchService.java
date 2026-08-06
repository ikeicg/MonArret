package com.catchmystm.backend.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.catchmystm.backend.dto.NextDepartureDto;
import com.catchmystm.backend.dto.NextDepartureRequest;

@Service
public class TripMatchService {
	
	@Autowired
	private NamedParameterJdbcTemplate jdbcTemplate ;
	
	private static final ZoneId MONTREAL_ZONE = ZoneId.of("America/Montreal");
	
	
	public Optional<NextDepartureDto> getNextDeparture(NextDepartureRequest departureRequest) {
		
		LocalDate today = LocalDate.now(MONTREAL_ZONE);
		LocalTime now = LocalTime.now(MONTREAL_ZONE);
		
		
		String sql = """
						WITH candidate_service_dates AS (

					    -- Yesterday's service dates
					    SELECT
					        sd.service_id,
					        -86400 AS day_offset
					    FROM gtfs_static.service_dates sd
					    WHERE sd.valid_date = :yesterday
					
					
					    UNION ALL
					
					
					    -- Today's service dates
					    SELECT
					        sd.service_id,
					        0 AS day_offset
					    FROM gtfs_static.service_dates sd
					    WHERE sd.valid_date = :today
					
					
					    UNION ALL
					
					
					    -- Tomorrow's service dates
					    SELECT
					        sd.service_id,
					        86400 AS day_offset
					    FROM gtfs_static.service_dates sd
					    WHERE sd.valid_date = :tomorrow
					)
					
					
					SELECT
					    st.trip_id,
					    t.route_id,
					    t.direction_id,
					    st.stop_id,
					    st.departure_time,
					    csd.day_offset,
					    st.departure_time + csd.day_offset AS effective_departure_time
					
					FROM gtfs_static.stop_times st
					
					JOIN gtfs_static.trips t
					    ON t.trip_id = st.trip_id
					
					JOIN candidate_service_dates csd
					    ON csd.service_id = t.service_id
					
					WHERE st.stop_id = :stop_id
					  AND t.route_id = :route_id
					  AND t.direction_id = :direction_id
				
						--- future departures
					  AND (st.departure_time + csd.day_offset) >= :current_time
					
					ORDER BY effective_departure_time
					
					LIMIT 1;
				""";
		
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("yesterday", today.minusDays(1))
				.addValue("today", today)
				.addValue("tomorrow", today.plusDays(1))
				.addValue("stop_id", departureRequest.stopId())
				.addValue("route_id", departureRequest.routeId())
				.addValue("direction_id", departureRequest.directionId())
				.addValue("current_time", now.toSecondOfDay());
		
		
		List<NextDepartureDto> result = jdbcTemplate.query(
				sql,
				params,
				(rs, rowNum) -> new NextDepartureDto(
						rs.getString("route_id"),
						rs.getInt("direction_id"),
						rs.getString("stop_id"),
						rs.getString("trip_id"),
						rs.getInt("effective_departure_time"),
						rs.getInt("day_offset")
						)
				);
		
		return result.stream().findFirst();
	}

}
