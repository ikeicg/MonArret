package com.catchmystm.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.catchmystm.backend.entity.CalendarDate;
import com.catchmystm.backend.entity.CalendarDateId;

@Repository
public interface CalendarDateRepository extends JpaRepository<CalendarDate, CalendarDateId> {
	
}