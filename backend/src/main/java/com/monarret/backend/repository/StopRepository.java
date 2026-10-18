package com.monarret.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.monarret.backend.entity.Stop;

@Repository
public interface StopRepository extends JpaRepository<Stop, String> {
    
    
}