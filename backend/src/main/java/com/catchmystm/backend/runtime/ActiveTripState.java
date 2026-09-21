package com.catchmystm.backend.runtime;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.locationtech.jts.geom.LineString;

import com.catchmystm.backend.runtime.model.GpsPing;
import com.catchmystm.backend.runtime.model.StopTimeContext;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActiveTripState {
	
	// Trip Identifiers
    private String tripId;
    private String routeId;
    private Integer directionId;

    // Static and Real-time Stop Schedule Context
    private List<StopTimeContext> stopSchedule;
    private LineString route;

    // Historical Sliding Window for GPS Ping
    private final Deque<GpsPing> recentPings = new ArrayDeque<>(5);

    // Temporal metrics
    private Instant lastSeenInFeed;
    private Instant lastGpsPingTime;

    // Spatial & Progress metrics
    private Double lastX;
    private Double lastY;
    private Float currentSpeedMps;
    private Integer currentStopSequence;
    private String currentStopId;
    private String currentStatus;

    // Trip lifecycle status 
    private String scheduleRelationship;

    // Calculated Engine Signals
    private double pingDecayScore;
    private String pingFreshnessStatus;
    
    private Double requiredSpeedMps;
    private String etaReliabilityStatus;
    
    private Double shapeDeviationMeters;
    private String routeDeviationStatus;
    
    private Double busBunchingScore;
    private String trailingVehicleId;      
    
    
    public void addGpsPing(double x, double y, Instant timestamp) {
        if (recentPings.size() >= 5) {
            recentPings.removeFirst();
        }
        recentPings.addLast(new GpsPing(x, y, timestamp));
    }

    public GpsPing getLatestPing() {
        return recentPings.peekLast();
    }

    // Audit methods
    public void feedTouch() {
        this.lastSeenInFeed = Instant.now();
    }

    public void gpsPingTouch(Instant feedTimestamp) {
    	this.lastGpsPingTime = feedTimestamp;
    }
}