package com.catchmystm.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.catchmystm.backend.dto.ApiResponse;
import com.catchmystm.backend.dto.NextDepartureDto;
import com.catchmystm.backend.dto.NextDepartureRequest;
import com.catchmystm.backend.service.TripMatchService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/search")
public class SearchController {
	
	@Autowired
	private TripMatchService tripMatchService;
	
	@PostMapping("/nextDeparture")
	public Mono<ApiResponse<NextDepartureDto>> getNextDepartureFromStop(@RequestBody NextDepartureRequest nextDepartureRequest){
		
		return Mono.fromCallable(() -> tripMatchService.getNextDeparture(nextDepartureRequest))
				.map(opt -> opt
					    .map(result -> new ApiResponse<NextDepartureDto>(
					            true,
					            "Next Departure Found",
					            result
					    ))
					    .orElseGet(() -> new ApiResponse<NextDepartureDto>(
					            false,
					            "No Trip found from this stop within 24 hours",
					            null
					    ))
					)
				.subscribeOn(Schedulers.boundedElastic());
	}
}
