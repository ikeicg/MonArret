package com.catchmystm.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.catchmystm.backend.dto.ApiResponse;
import com.catchmystm.backend.dto.DirectionDto;
import com.catchmystm.backend.dto.RouteDto;
import com.catchmystm.backend.dto.StopTimeDto;
import com.catchmystm.backend.service.SearchService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {
	
	private final SearchService searchService;


    @GetMapping("/route/{routeId}")
    public Mono<ApiResponse<RouteDto>> getRouteFromId(
            @PathVariable String routeId) {

        return Mono.fromCallable(() -> searchService.getRouteFromId(routeId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    
    @GetMapping("/route-directions/{routeId}")
    public Mono<ApiResponse<List<DirectionDto>>> getDirectionsFromRouteId(
            @PathVariable String routeId) {

        return Mono.fromCallable(() -> searchService.getDirectionsFromRouteId(routeId))
                .subscribeOn(Schedulers.boundedElastic());
    }
    
    
    @GetMapping("/stop-times")
    public Mono<ApiResponse<List<StopTimeDto>>> getNextTripsFromRouteDirection(
    		@RequestParam String routeId, @RequestParam Integer directionId) {
    	
    	return Mono.fromCallable(() -> searchService.findDeparturesWithin1Hour(routeId, directionId))
    			.subscribeOn(Schedulers.boundedElastic());
    }
}
