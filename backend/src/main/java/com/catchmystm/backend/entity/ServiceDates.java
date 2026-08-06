package com.catchmystm.backend.entity;

import java.time.Instant;

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
@Table(name="service_dates", schema="gtfs_static")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceDates {
	
	@EmbeddedId
	private ServiceDatesId id;
	
	@Column(name="created_at", nullable=false)
	private Instant createdAt;
	
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("serviceId")
    @JoinColumn(name = "service_id")
    private Calendar calendar;

}
