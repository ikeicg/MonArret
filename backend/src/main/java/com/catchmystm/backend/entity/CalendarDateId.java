package com.catchmystm.backend.entity;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarDateId implements Serializable {

    @Column(name = "service_id")
    private String serviceId;

    @Column(name = "exception_date")
    private LocalDate exceptionDate;
}