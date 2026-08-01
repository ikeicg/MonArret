package com.catchmystm.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.catchmystm.backend.entity.Shape;
import com.catchmystm.backend.entity.ShapeId;

@Repository
public interface ShapeRepository extends JpaRepository<Shape, ShapeId> {
    
}