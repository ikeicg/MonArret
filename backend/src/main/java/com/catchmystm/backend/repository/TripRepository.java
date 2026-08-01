package com.catchmystm.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.catchmystm.backend.entity.Trip;

@Repository
public interface TripRepository extends JpaRepository<Trip, String> {
    
}