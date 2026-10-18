package com.monarret.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.config.MeterFilter;

@Configuration
public class MetricsConfiguration {

	// Spring's own meters number a few hundred. A tag fed with unbounded values (trip ids, urls) would blow past
	// this quickly, and the registry then refuses new meters instead of growing until the heap is gone.
	static final int MAX_METERS = 2000;

	@Bean
	MeterFilter meterLimit() {
		return MeterFilter.maximumAllowableMetrics(MAX_METERS);
	}
}
