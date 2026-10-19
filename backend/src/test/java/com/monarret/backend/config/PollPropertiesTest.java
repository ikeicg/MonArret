package com.monarret.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class PollPropertiesTest {

	@Configuration
	@EnableConfigurationProperties(PollProperties.class)
	static class Config {
	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);
	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void defaultsToTwentySeconds() {
		runner.run(context -> assertThat(context.getBean(PollProperties.class).interval())
				.isEqualTo(Duration.ofSeconds(20)));
	}

	@Test
	void readsAnIntervalFromTheEnvironment() {
		runner.withPropertyValues("stm.poll.interval=30s").run(context -> assertThat(context)
				.hasSingleBean(PollProperties.class)
				.getBean(PollProperties.class).extracting(PollProperties::interval).isEqualTo(Duration.ofSeconds(30)));
	}

	@Test
	void anIntervalBelowTheQuotaFloor_stopsTheApplicationFromStarting() {
		runner.withPropertyValues("stm.poll.interval=19s").run(context -> assertThat(context).hasFailed());
	}

	@Test
	void exactlyTheFloor_isAccepted() {
		assertThat(validator.validate(new PollProperties(Duration.ofSeconds(20)))).isEmpty();
		assertThat(validator.validate(new PollProperties(Duration.ofSeconds(19)))).hasSize(1);
	}

	@Test
	void requestsPerDay_countsBothFeeds() {
		assertThat(new PollProperties(Duration.ofSeconds(20)).requestsPerDay()).isEqualTo(8_640);
		assertThat(new PollProperties(Duration.ofSeconds(30)).requestsPerDay()).isEqualTo(5_760);
		assertThat(new PollProperties(Duration.ofSeconds(20)).requestsPerDay())
				.isLessThan(PollProperties.STM_DAILY_QUOTA);
	}

	@Test
	void anIntervalAboveFortySeconds_isStillAcceptedButFlagged() {
		runner.withPropertyValues("stm.poll.interval=120s").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(PollProperties.class).exceedsRecommendedMax()).isTrue();
		});
		assertThat(new PollProperties(Duration.ofSeconds(40)).exceedsRecommendedMax()).isFalse();
	}
}
