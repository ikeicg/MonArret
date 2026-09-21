package com.catchmystm.backend.runtime;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.stereotype.Component;

import com.catchmystm.backend.entity.Shape;
import com.catchmystm.backend.entity.StopTime;
import com.catchmystm.backend.entity.Trip;
import com.catchmystm.backend.repository.ShapeRepository;
import com.catchmystm.backend.repository.TripRepository;
import com.catchmystm.backend.runtime.model.StopTimeContext;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ActiveTripStateFactory {

	private final TripRepository tripRepository;
	private final ShapeRepository shapeRepository;
	private final GeometricOperationsService geoOpsService;
	
	
	public ActiveTripState create(String tripId) {
		
			ActiveTripState activeTrip = new ActiveTripState();
			Trip baseTrip = tripRepository.findById(tripId).orElseThrow(() -> new IllegalStateException("Trip not found: " + tripId));
			
			// Populate identifiers
			activeTrip.setTripId(tripId);
			activeTrip.setRouteId(baseTrip.getRouteId());
			activeTrip.setDirectionId(baseTrip.getDirectionId());
			
			// Populate stopSchedule
			List<StopTimeContext> schedule = generateSchedule(baseTrip.getStopTimes());
			
			if(schedule.isEmpty()) throw new IllegalStateException();
			
			activeTrip.setStopSchedule(schedule);
			
			// Populate route
			LineString route = generateRoute(baseTrip.getRoutePatternId());
			
			if(route == null) throw new IllegalStateException();
			
			activeTrip.setRoute(route);
			
			return activeTrip;
	}
	
	private List<StopTimeContext> generateSchedule(List<StopTime> stopTimes){

		List<StopTimeContext> schedule = stopTimes.stream()
				.map(this::constructStopTimeContext)
				.collect(Collectors.toCollection(ArrayList::new));

		return schedule;
	}
	
	private LineString generateRoute(String routePattern) {
		
		List<Shape> shapes = shapeRepository.findByRoutePatternIdOrderByIdShapePtSequenceAsc(routePattern);
		
		if(shapes.isEmpty()) return null;
		
		List<Point> points = shapes.stream()
								.map(x -> x.getShapeLocation())
								.toList();
		
		return geoOpsService.buildLineStringFromPoint(points);
	}
	
	private StopTimeContext constructStopTimeContext(StopTime stopTime) {
		
		String stopId = stopTime.getStopId();
		Integer stopSeq = stopTime.getId().getStopSequence();
		
		Integer arrivalSeconds = stopTime.getArrivalTime();
		LocalTime arrivalTime = arrivalSeconds >= 86400 ? LocalTime.ofSecondOfDay(arrivalSeconds % 86400) : LocalTime.ofSecondOfDay(arrivalSeconds);
		
		Integer departureSeconds = stopTime.getDepartureTime();
		LocalTime departureTime = departureSeconds >= 86400 ? LocalTime.ofSecondOfDay(departureSeconds % 86400) : LocalTime.ofSecondOfDay(departureSeconds);
		
		Point stopLocation = stopTime.getStop().getStopLocation();
		ProjCoordinate projectedLocation = geoOpsService.transformWgsToUtm(stopLocation.getX(), stopLocation.getY());
		stopLocation = geoOpsService.convertProjCoordinateToPoint(projectedLocation);
		
		return new StopTimeContext(stopId, stopSeq, arrivalTime, arrivalTime, departureTime, departureTime,
				stopLocation.getX(), stopLocation.getY(), "SCHEDULED");

	}

	
	public ActiveTripState createBare(String tripId, String routeId, Integer directionId) {

		ActiveTripState activeTrip = new ActiveTripState();
		activeTrip.setTripId(tripId);
		activeTrip.setRouteId(routeId);
		activeTrip.setDirectionId(directionId);

		return activeTrip;
	}
}
