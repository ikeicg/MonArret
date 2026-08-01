package com.catchmystm.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.catchmystm.backend.entity.Agency;

@Repository
public interface AgencyRepository extends JpaRepository<Agency, String> {
    
}