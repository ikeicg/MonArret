package com.monarret.backend.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * How often the two STM feeds are polled. STM allows 10,000 requests a day per user across all applications, so the
 * lower bound keeps one stack within it. The off-route rule needs 3 positions in a 120 s window, which stops holding
 * above 40 s.
 */
@Validated
@ConfigurationProperties(prefix = "stm.poll")
public record PollProperties(
		@DefaultValue("20s") @DurationMin(seconds = PollProperties.MIN_SECONDS) Duration interval) {

	public static final long MIN_SECONDS = 20;
	public static final long MAX_RECOMMENDED_SECONDS = 40;
	public static final int FEEDS = 2;
	public static final int STM_DAILY_QUOTA = 10_000;

	public long requestsPerDay() {
		return FEEDS * (Duration.ofDays(1).toSeconds() / interval.toSeconds());
	}

	public boolean exceedsRecommendedMax() {
		return interval.toSeconds() > MAX_RECOMMENDED_SECONDS;
	}
}
