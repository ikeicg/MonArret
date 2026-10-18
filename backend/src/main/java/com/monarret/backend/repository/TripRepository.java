package com.monarret.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.monarret.backend.entity.Trip;

@Repository
public interface TripRepository extends JpaRepository<Trip, String> {
	
	@EntityGraph(attributePaths = {
			"stopTimes",
			"stopTimes.stop"
	})
	Optional<Trip> findById(String tripId);
    
}