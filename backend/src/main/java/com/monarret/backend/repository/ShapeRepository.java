package com.monarret.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.monarret.backend.entity.Shape;
import com.monarret.backend.entity.ShapeId;

@Repository
public interface ShapeRepository extends JpaRepository<Shape, ShapeId> {
    
	public List<Shape> findByRoutePatternIdOrderByIdShapePtSequenceAsc(String routePatternId);
}