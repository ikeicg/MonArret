package com.monarret.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.monarret.backend.entity.Agency;

@Repository
public interface AgencyRepository extends JpaRepository<Agency, String> {
    
}