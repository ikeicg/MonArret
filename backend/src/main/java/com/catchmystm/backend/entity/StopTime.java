package com.catchmystm.backend.entity;

import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stop_times", schema = "gtfs_static")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StopTime {
    @EmbeddedId
    private StopTimeId id;

    @Column(name = "arrival_time")
    private Integer arrivalTime;

    @Column(name = "departure_time")
    private Integer departureTime;

    @Column(name = "stop_id")
    private String stopId;
    
    @Column(name = "pickup_type")
    private Integer pickupType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tripId")
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stop_id", insertable = false, updatable = false)
    private Stop stop;
}