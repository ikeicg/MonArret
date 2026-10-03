package com.catchmystm.backend.runtime;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.locationtech.jts.geom.LineString;

import com.catchmystm.backend.runtime.model.GpsPing;
import com.catchmystm.backend.runtime.model.StopTimeContext;
import com.catchmystm.backend.runtime.model.TripSnapshot;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class ActiveTripState {
	
	// Trip Identifiers
    private String tripId;
    private String routeId;
    private Integer directionId;
    private String shapeId;
    private LocalDate serviceDate;

    // Static and Real-time Stop Schedule Context
    private List<StopTimeContext> stopSchedule;
    private LineString route;
    private Double routeLengthMeters;

    // Time-based window of distinct-timestamp pings, measured against the vehicle's own latest timestamp
    public static final Duration PING_WINDOW = Duration.ofSeconds(120);
    private final Deque<GpsPing> recentPings = new ArrayDeque<>();

    // When the feed last mentioned this trip; drives store eviction
    private Instant lastSeenInFeed;

    // Progress as the vehicle reports it
    private Integer currentStopSequence;
    private String currentStopId;
    private String currentStatus;
    private String occupancyStatus;

    // Trip lifecycle status
    private String scheduleRelationship;

    // The feed says the vehicle is moving but its position has not changed, so the position is unusable
    public static final float MOVING_SPEED_MPS = 0.5f;
    public static final double FROZEN_MOVEMENT_METERS = 2.0;
    private boolean positionFrozen;

    // Bunching result, held on the leader: the time gap to the bus directly behind it and how many
    // consecutive cycles some bus has been close behind.
    public static final int BUNCHING_CONFIRM_CYCLES = 3;
    private Double bunchingGapSeconds;
    private int consecutiveCloseCycles;


    public void recordCloseFollower(double gapSeconds) {
        this.consecutiveCloseCycles++;
        this.bunchingGapSeconds = gapSeconds;
    }

    // No close follower this cycle, or it could not be judged.
    public void clearFollower() {
        this.bunchingGapSeconds = null;
        this.consecutiveCloseCycles = 0;
    }

    public boolean isBunchingConfirmed() {
        return consecutiveCloseCycles >= BUNCHING_CONFIRM_CYCLES;
    }


    // Returns false when the ping is not newer than the latest one (repeat or out of order) and is dropped.
    public boolean addGpsPing(double x, double y, Instant timestamp, Double alongRoute, Double offRouteMeters) {
        GpsPing latest = recentPings.peekLast();
        if (latest != null && !timestamp.isAfter(latest.timestamp())) {
            return false;
        }
        recentPings.addLast(new GpsPing(x, y, timestamp, alongRoute, offRouteMeters));

        Instant cutoff = timestamp.minus(PING_WINDOW);
        while (recentPings.peekFirst().timestamp().isBefore(cutoff)) {
            recentPings.removeFirst();
        }
        return true;
    }

    public GpsPing getLatestPing() {
        return recentPings.peekLast();
    }

    // Meters moved between the oldest and newest ping in the window (along the route when both have it);
    // null with fewer than two pings.
    public Double metersMovedInWindow() {
        if (recentPings.size() < 2) return null;
        GpsPing first = recentPings.peekFirst();
        GpsPing last = recentPings.peekLast();
        if (first.alongRoute() != null && last.alongRoute() != null) {
            return Math.abs(last.alongRoute() - first.alongRoute());
        }
        return Math.hypot(last.x() - first.x(), last.y() - first.y());
    }

    // Called after each accepted ping with the speed the feed published for it (null when absent).
    public void updatePositionFrozen(Float publishedSpeedMps) {
        Double moved = metersMovedInWindow();
        this.positionFrozen = publishedSpeedMps != null && publishedSpeedMps > MOVING_SPEED_MPS
                && moved != null && moved < FROZEN_MOVEMENT_METERS;
    }

    // Must run under the store's per-trip lock (ActiveTripStateStore.snapshotTrip).
    public TripSnapshot toSnapshot() {
        return new TripSnapshot(tripId, routeId, directionId, shapeId, serviceDate, routeLengthMeters,
                stopSchedule == null ? List.of() : List.copyOf(stopSchedule),
                List.copyOf(recentPings),
                lastSeenInFeed, currentStopSequence, currentStopId, currentStatus, occupancyStatus,
                scheduleRelationship, positionFrozen, bunchingGapSeconds, consecutiveCloseCycles,
                isBunchingConfirmed());
    }

    // Audit methods
    public void feedTouch() {
        this.lastSeenInFeed = Instant.now();
    }
}