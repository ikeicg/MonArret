package com.catchmystm.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.catchmystm.backend.entity.RoutePattern;

@Repository
public interface RoutePatternRepository extends JpaRepository<RoutePattern, String> {
    
}