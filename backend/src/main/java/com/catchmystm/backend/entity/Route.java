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
@Table(name = "routes", schema = "gtfs_static")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Route {
    @Id
    @Column(name = "route_id")
    private String routeId;

    @Column(name = "agency_id", nullable = false)
    private String agencyId;

    @Column(name = "route_short_name")
    private String routeShortName;

    @Column(name = "route_long_name")
    private String routeLongName;

    @Column(name = "route_type", nullable = false)
    private Integer routeType;

    @Column(name = "route_url")
    private String routeUrl;

    @Column(name = "route_color")
    private String routeColor;

    @Column(name = "route_text_color")
    private String routeTextColor;

    @Column(name = "route_desc")
    private String routeDesc;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id", insertable = false, updatable = false)
    private Agency agency;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.List<Trip> trips;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.List<Direction> directions;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.List<RoutePattern> routePatterns;
}