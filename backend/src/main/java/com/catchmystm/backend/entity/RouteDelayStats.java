package com.catchmystm.backend.entity;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table("route_delay_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteDelayStats {

    @Id
    private Long id;

    @Column("route_id")
    private String routeId;

    @Column("direction_id")
    private Integer directionId;

    @Column("hour_bucket")
    private Short hourBucket;

    @Column("sample_count")
    private Long sampleCount = 0L;

    @Column("mean_delay")
    private Double meanDelay = 0.0;

    @Column("m2")
    private Double m2 = 0.0;

    @Column("last_reset_date")
    private LocalDate lastResetDate;

    @Version
    private Long version = 0L;
    
    public void addObservation(Double delayInSeconds) {
    	
    	sampleCount++;
    	
    	Double distanceFromMean = delayInSeconds - meanDelay;
    	
    	meanDelay = meanDelay + (distanceFromMean / sampleCount);
    	
    	m2 = m2 + (distanceFromMean * (delayInSeconds - meanDelay));
    	
    }
    
    public Double getStandardDeviation() {
    	
    	if(sampleCount == 0) return 0.0;
    	
    	Double variance = m2 / sampleCount;
    	
    	return Math.sqrt(variance);
    }
    
    public void resetState(LocalDate today) {
    	
    	lastResetDate = today;
    	m2 = 0.0;
    	meanDelay = 0.0;
    	sampleCount = 0L;
    	
    }
}