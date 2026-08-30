package com.catchmystm.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.catchmystm.backend.dto.ApiResponse;
import com.catchmystm.backend.dto.DirectionDto;
import com.catchmystm.backend.dto.RouteDto;
import com.catchmystm.backend.dto.StopTimeDto;
import com.catchmystm.backend.repository.DirectionRepository;
import com.catchmystm.backend.repository.RouteRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SearchService {

	private static final int BUS_ROUTE_TYPE = 3;
	
	private final NamedParameterJdbcTemplate jdbcTemplate;

	private final Clock montrealClock;

    private final RouteRepository routeRepo;
    private final DirectionRepository directionRepo;
    
    private final DepartureService departService;

    @Cacheable("routes")
    public ApiResponse<RouteDto> getRouteFromId(String routeId) {

        return routeRepo.findById(routeId)
                .filter(route -> route.getRouteType() == BUS_ROUTE_TYPE)
                .map(route -> new ApiResponse<RouteDto>(
                        true,
                        "Route Found",
                        new RouteDto(
                                route.getRouteId(),
                                route.getRouteLongName()
                        )
                ))
                .orElseGet(() -> new ApiResponse<RouteDto>(
                        false,
                        "No Bus Route found",
                        null
                ));
    }
    
    @Cacheable("route-directions")
    public ApiResponse<List<DirectionDto>> getDirectionsFromRouteId(String routeId) {

        return routeRepo.findById(routeId)
                .filter(route -> route.getRouteType() == BUS_ROUTE_TYPE)
                .map(route -> {
                    List<DirectionDto> directions = directionRepo
                    		.findByRouteId(routeId)
                    		.stream()
                    		.map(item -> new DirectionDto(
                    				item.getRouteId(),
                    				item.getDirectionId(),
                    				item.getDirection()
                    				))
                    		.collect(Collectors.toList());

                    boolean success = !directions.isEmpty();
                    String message = success
                            ? "Directions found"
                            : "No Directions found for this bus route";

                    return new ApiResponse<List<DirectionDto>>(
                            success,
                            message,
                            directions
                    );
                })
                .orElseGet(() -> new ApiResponse<List<DirectionDto>>(
                        false,
                        "No Bus Route found",
                        null
                ));
    }

    public ApiResponse<List<StopTimeDto>> findDeparturesWithin1Hour(String routeId, Integer directionId) {
    	
    	LocalDateTime start = LocalDateTime.now(montrealClock);
    	
    	List<StopTimeDto> result = departService.findDeparturesForToday(
    			routeId,
    			directionId,
    			LocalDate.now(montrealClock).toString()
    			);
    	
    	result = result
    			.stream()
    			.filter(item -> {
    				long duration = ChronoUnit.MINUTES.between(start, item.departureTime());
    				return duration >= 0 && duration <= 60;
    			})
    			.collect(Collectors.toList());
    	
      boolean success = !result.isEmpty();

      String message = success
              ? "Trip Departures within an hour"
              : "No Trips Found on this route for the next Hour";

      return new ApiResponse<>(
              success,
              message,
              result
      );
    	
    	
    }
}