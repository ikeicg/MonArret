package com.monarret.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.monarret.backend.entity.CalendarDate;
import com.monarret.backend.entity.CalendarDateId;

@Repository
public interface CalendarDateRepository extends JpaRepository<CalendarDate, CalendarDateId> {
	
}