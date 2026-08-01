package com.catchmystm.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "directions", schema = "gtfs_static")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Direction {
    @Id
    @Column(name = "route_direction_id")
    private String routeDirectionId;

    @Column(name = "route_id", nullable = false)
    private String routeId;

    @Column(name = "direction_id", nullable = false)
    private Integer directionId;

    @Column(name = "direction")
    private String direction;

    @Column(name = "direction_legacy")
    private String directionLegacy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", insertable = false, updatable = false)
    private Route route;
}