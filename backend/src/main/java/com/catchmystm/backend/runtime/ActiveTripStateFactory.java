package com.catchmystm.backend.runtime;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

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
	private final Clock montrealClock;
	
	
	public ActiveTripState create(String tripId, LocalDate serviceDate) {
		
			ActiveTripState activeTrip = new ActiveTripState();
			Trip baseTrip = tripRepository.findById(tripId).orElseThrow(() -> new IllegalStateException("Trip not found: " + tripId));
			
			// Populate identifiers
			activeTrip.setTripId(tripId);
			activeTrip.setRouteId(baseTrip.getRouteId());
			activeTrip.setDirectionId(baseTrip.getDirectionId());
			activeTrip.setShapeId(baseTrip.getShapeId());
			activeTrip.setServiceDate(serviceDate);

			// Populate route
			LineString route = generateRoute(baseTrip.getRoutePatternId());
			
			if(route == null) throw new IllegalStateException();
			
			activeTrip.setRoute(route);
			activeTrip.setRouteLengthMeters(route.getLength());
			
			// Populate stopSchedule
			List<StopTimeContext> schedule = generateSchedule(baseTrip.getStopTimes(), serviceDate, route);
			
			if(schedule.isEmpty()) throw new IllegalStateException();
			
			activeTrip.setStopSchedule(schedule);
			
			return activeTrip;
	}
	
	private List<StopTimeContext> generateSchedule(List<StopTime> stopTimes, LocalDate serviceDate, LineString route){

		List<StopTimeContext> schedule = new ArrayList<>();
		double previousDistance = 0;

		// Stops are in sequence order, so each is searched for only at or beyond the previous one (loop routes).
		for (StopTime stopTime : stopTimes) {
			StopTimeContext context = constructStopTimeContext(stopTime, serviceDate, route, previousDistance);
			schedule.add(context);
			previousDistance = context.distanceAlongRoute();
		}

		return schedule;
	}
	
	private LineString generateRoute(String routePattern) {
		
		List<Shape> shapes = shapeRepository.findByRoutePatternIdOrderByIdShapePtSequenceAsc(routePattern);
		
		if(shapes.isEmpty()) return null;
		
		List<Point> points = shapes.stream()
								.map(x -> x.getShapeLocation())
								.map(x -> geoOpsService.convertProjCoordinateToPoint(geoOpsService.transformWgsToUtm(x.getX(), x.getY())))
								.toList();
		
		return geoOpsService.buildLineStringFromPoint(points);
	}
	
	private StopTimeContext constructStopTimeContext(StopTime stopTime, LocalDate serviceDate, LineString route,
			double minDistanceAlongRoute) {
		
		String stopId = stopTime.getStopId();
		Integer stopSeq = stopTime.getId().getStopSequence();
		
		Integer arrivalSeconds = stopTime.getArrivalTime() != null ? stopTime.getArrivalTime() : stopTime.getDepartureTime();
		Integer departureSeconds = stopTime.getDepartureTime() != null ? stopTime.getDepartureTime() : stopTime.getArrivalTime();
		Instant arrivalTime = toInstant(serviceDate, arrivalSeconds);
		Instant departureTime = toInstant(serviceDate, departureSeconds);
		
		Point stopLocation = stopTime.getStop().getStopLocation();
		ProjCoordinate projectedLocation = geoOpsService.transformWgsToUtm(stopLocation.getX(), stopLocation.getY());
		stopLocation = geoOpsService.convertProjCoordinateToPoint(projectedLocation);
		
		double distanceAlongRoute = geoOpsService.distanceAlongRouteAfter(route, stopLocation.getX(), stopLocation.getY(),
				minDistanceAlongRoute);
		
		return new StopTimeContext(stopId, stopSeq, arrivalTime, arrivalTime, departureTime, departureTime,
				stopLocation.getX(), stopLocation.getY(), distanceAlongRoute, "SCHEDULED");

	}

	
	// GTFS times count from noon minus 12h of the service date and can exceed 24h; this only differs from midnight on DST days.
	private Instant toInstant(LocalDate serviceDate, int secondsOfServiceDay) {
		ZoneId zone = montrealClock.getZone();
		return serviceDate.atTime(12, 0).atZone(zone).minusHours(12).plusSeconds(secondsOfServiceDay).toInstant();
	}

	
	public ActiveTripState createBare(String tripId, String routeId, Integer directionId, LocalDate serviceDate) {

		ActiveTripState activeTrip = new ActiveTripState();
		activeTrip.setTripId(tripId);
		activeTrip.setRouteId(routeId);
		activeTrip.setDirectionId(directionId);
		activeTrip.setServiceDate(serviceDate);

		return activeTrip;
	}
}
