package com.catchmystm.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.catchmystm.backend.entity.Direction;

@Repository
public interface DirectionRepository extends JpaRepository<Direction, String> {
	
	List<Direction> findByRouteId(String routeId);
    
}