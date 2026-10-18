package com.monarret.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.monarret.backend.entity.StopTime;
import com.monarret.backend.entity.StopTimeId;

@Repository
public interface StopTimeRepository extends JpaRepository<StopTime, StopTimeId> {
    
}