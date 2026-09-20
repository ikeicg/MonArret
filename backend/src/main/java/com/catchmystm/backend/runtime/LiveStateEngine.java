package com.catchmystm.backend.runtime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.catchmystm.backend.service.StmPoller;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Component
public class LiveStateEngine {
	
	private final Flux<FeedMessage> tripUpdatesFeed;
	private final Flux<FeedMessage> vehiclePositionsFeed;
	
	private final Sinks.Many<LiveTripUpdate> sink = Sinks.many().multicast().directBestEffort();
	
	public LiveStateEngine(StmPoller poller) {
		tripUpdatesFeed = poller.getTripUpdatesPublisher();
		vehiclePositionsFeed = poller.getVehiclePositionsPublisher();
	}
	
	@EventListener(ApplicationReadyEvent.class)
	private void start() {
		
		tripUpdatesFeed
			.publishOn(Schedulers.boundedElastic())
			.subscribe(feedMessage -> {
				handleTripUpdates(feedMessage);
			});
		
		vehiclePositionsFeed
			.publishOn(Schedulers.boundedElastic())
			.subscribe(feedMessage -> {
				handleVehiclePositions(feedMessage);
			});
		
	}
	
	private void handleTripUpdates(FeedMessage feed) {
		
		if(feed == null) return;
		
		for(FeedEntity entity : feed.getEntityList()) {
			
			if(!entity.hasTripUpdate()) continue;
			
			
		}
		
	}
	
	private void handleVehiclePositions(FeedMessage feed) {
		
		if(feed == null) return;
		
		for(FeedEntity entity : feed.getEntityList()) {
			
			if(!entity.hasVehicle()) continue;
			
			
		}
	}
	
	
	private void publishUpdate(LiveTripUpdate tripUpdate) {
		if(tripUpdate != null) {
			sink.tryEmitNext(tripUpdate);
		}
	}
	
	public Flux<LiveTripUpdate> getPublisher(){
		return sink.asFlux();
	}

}
