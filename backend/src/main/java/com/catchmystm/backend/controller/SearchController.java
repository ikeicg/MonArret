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

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

	private final SearchService searchService;


    @GetMapping("/route/{routeId}")
    public ApiResponse<RouteDto> getRouteFromId(
            @PathVariable String routeId) {

        return searchService.getRouteFromId(routeId);
    }


    @GetMapping("/route-directions/{routeId}")
    public ApiResponse<List<DirectionDto>> getDirectionsFromRouteId(
            @PathVariable String routeId) {

        return searchService.getDirectionsFromRouteId(routeId);
    }


    @GetMapping("/stop-times")
    public ApiResponse<List<StopTimeDto>> getNextTripsFromRouteDirection(
    		@RequestParam String routeId, @RequestParam Integer directionId) {

    	return searchService.findDeparturesWithin1Hour(routeId, directionId);
    }
}
