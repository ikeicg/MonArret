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
@Table(name = "route_patterns", schema = "gtfs_static")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutePattern {
    @Id
    @Column(name = "route_pattern_id")
    private String routePatternId;

    @Column(name = "route_id", nullable = false)
    private String routeId;

    @Column(name = "direction_id")
    private Integer directionId;

    @Column(name = "route_pattern_typicality")
    private Integer routePatternTypicality;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", insertable = false, updatable = false)
    private Route route;

    @OneToMany(mappedBy = "routePattern", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.List<Shape> shapes;

    @OneToMany(mappedBy = "routePattern", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.List<Trip> trips;
}