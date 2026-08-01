package com.catchmystm.backend.entity;

import java.time.Instant;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trips", schema = "gtfs_static")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {
    @Id
    @Column(name = "trip_id")
    private String tripId;

    @Column(name = "route_id", nullable = false)
    private String routeId;

    @Column(name = "service_id", nullable = false)
    private String serviceId;

    @Column(name = "trip_headsign")
    private String tripHeadsign;

    @Column(name = "direction_id")
    private Integer directionId;

    @Column(name = "shape_id")
    private String shapeId;

    @Column(name = "wheelchair_accessible")
    private Integer wheelchairAccessible;

    @Column(name = "route_pattern_id")
    private String routePatternId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", insertable = false, updatable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", insertable = false, updatable = false)
    private Calendar calendar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_pattern_id", insertable = false, updatable = false)
    private RoutePattern routePattern;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.List<StopTime> stopTimes;
}