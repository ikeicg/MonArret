package com.catchmystm.backend.entity;

import java.time.Instant;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "agency", schema = "gtfs_static")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agency {
    @Id
    @Column(name = "agency_id")
    private String agencyId;

    @Column(name = "agency_name", nullable = false)
    private String agencyName;

    @Column(name = "agency_url")
    private String agencyUrl;

    @Column(name = "agency_timezone")
    private String agencyTimezone;

    @Column(name = "agency_lang")
    private String agencyLang;

    @Column(name = "agency_phone")
    private String agencyPhone;

    @Column(name = "agency_fare_url")
    private String agencyFareUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "agency", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.List<Route> routes;
}
