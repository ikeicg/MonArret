package com.catchmystm.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.catchmystm.backend.entity.AppConfig;

@Repository
public interface AppConfigRepository extends JpaRepository<AppConfig, String> {

}
