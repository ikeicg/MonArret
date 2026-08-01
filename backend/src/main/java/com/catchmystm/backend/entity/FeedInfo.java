package com.catchmystm.backend.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "feed_info", schema = "gtfs_static")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feed_publisher_name")
    private String feedPublisherName;

    @Column(name = "feed_publisher_url")
    private String feedPublisherUrl;

    @Column(name = "feed_lang")
    private String feedLang;

    @Column(name = "feed_start_date")
    private LocalDate feedStartDate;

    @Column(name = "feed_end_date")
    private LocalDate feedEndDate;

    @Column(name = "feed_version")
    private String feedVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}