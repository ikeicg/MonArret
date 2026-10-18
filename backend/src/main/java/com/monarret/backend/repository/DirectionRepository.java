package com.monarret.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.monarret.backend.entity.Direction;

@Repository
public interface DirectionRepository extends JpaRepository<Direction, String> {
	
	List<Direction> findByRouteId(String routeId);
    
}