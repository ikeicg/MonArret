package com.catchmystm.backend.entity;


import java.time.Instant;
import java.util.List;

import org.locationtech.jts.geom.Point;

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
@Table(name = "stops", schema = "gtfs_static")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stop {
    @Id
    @Column(name = "stop_id")
    private String stopId;

    @Column(name = "stop_code")
    private String stopCode;

    @Column(name = "stop_name", nullable = false)
    private String stopName;

    @Column(name = "stop_location", nullable = false, columnDefinition = "geometry(Point, 4326)")
    private Point stopLocation;

    @Column(name = "stop_url")
    private String stopUrl;

    @Column(name = "location_type")
    private Integer locationType;

    @Column(name = "parent_station")
    private String parentStation;

    @Column(name = "wheelchair_boarding")
    private Integer wheelchairBoarding;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "stop", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<StopTime> stopTimes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_station", insertable = false, updatable = false)
    private Stop parentStopStation;
    
    @OneToMany(mappedBy = "parentStopStation", fetch = FetchType.LAZY)
    private List<Stop> childStops;
}