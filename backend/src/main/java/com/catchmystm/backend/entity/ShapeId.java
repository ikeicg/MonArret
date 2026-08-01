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
public class ShapeId implements Serializable {
	
	@Column(name = "shape_id")
    private String shapeId;
	
	@Column(name = "shape_pt_sequence")
    private Integer shapePtSequence;
}