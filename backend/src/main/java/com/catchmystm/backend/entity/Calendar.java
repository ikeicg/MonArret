package com.catchmystm.backend.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
@Table(name = "calendar", schema = "gtfs_static")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Calendar {
    @Id
    @Column(name = "service_id")
    private String serviceId;

    @Column(name = "monday", nullable = false)
    private Integer monday;

    @Column(name = "tuesday", nullable = false)
    private Integer tuesday;

    @Column(name = "wednesday", nullable = false)
    private Integer wednesday;

    @Column(name = "thursday", nullable = false)
    private Integer thursday;

    @Column(name = "friday", nullable = false)
    private Integer friday;

    @Column(name = "saturday", nullable = false)
    private Integer saturday;

    @Column(name = "sunday", nullable = false)
    private Integer sunday;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CalendarDate> calendarDates;
    
    @OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ServiceDates> serviceDates;

    @OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Trip> trips;
}