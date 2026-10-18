package com.monarret.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.monarret.backend.entity.AppConfig;

@Repository
public interface AppConfigRepository extends JpaRepository<AppConfig, String> {

}
