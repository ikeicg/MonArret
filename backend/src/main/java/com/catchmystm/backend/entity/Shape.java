package com.catchmystm.backend.entity;

import java.time.Instant;

import org.locationtech.jts.geom.Point;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shapes", schema = "gtfs_static")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shape {
    @EmbeddedId
    private ShapeId id;

    @Column(name = "shape_location", nullable = false, columnDefinition = "geometry(Point, 4326)")
    private Point shapeLocation;

    @Column(name = "route_pattern_id")
    private String routePatternId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_pattern_id", insertable = false, updatable = false)
    private RoutePattern routePattern;
}