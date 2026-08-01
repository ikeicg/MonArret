package com.catchmystm.backend.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StopTimeId implements Serializable {
	
	@Column(name = "trip_id")
    private String tripId;
	
	@Column(name = "stop_sequence")
    private Integer stopSequence;
}