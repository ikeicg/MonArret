package com.catchmystm.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.catchmystm.backend.entity.StopTime;
import com.catchmystm.backend.entity.StopTimeId;

@Repository
public interface StopTimeRepository extends JpaRepository<StopTime, StopTimeId> {
    
}