package com.monarret.backend.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class IngestMetricsTest {

	private static final long ONE_MS = 1_000_000L;

	private SimpleMeterRegistry registry;
	private IngestMetrics metrics;

	@BeforeEach
	void setUp() {
		registry = new SimpleMeterRegistry();
		metrics = new IngestMetrics("tripUpdates", "trip update", registry);
	}

	@Test
	void beforeTheFirstMessage_nothingIsRecorded() {
		assertThat(metrics.lastMillis()).isEqualTo(-1);
		assertThat(metrics.firstMillis()).isEqualTo(-1);
		assertThat(registry.get("feed.ingest.last.ms").tag("feed", "tripUpdates").gauge().value()).isNaN();
		assertThat(registry.get("feed.ingest.first.ms").tag("feed", "tripUpdates").gauge().value()).isNaN();
	}

	@Test
	void everyMessageIsTimed_andOnlyTheFirstIsKeptAsTheColdStart() {
		metrics.record(1300, System.nanoTime() - 500 * ONE_MS);
		metrics.record(1300, System.nanoTime() - 5 * ONE_MS);

		assertThat(registry.get("feed.ingest").tag("feed", "tripUpdates").timer().count()).isEqualTo(2);
		assertThat(metrics.firstMillis()).isGreaterThanOrEqualTo(500);
		assertThat(metrics.lastMillis()).isLessThan(metrics.firstMillis());
		assertThat(registry.get("feed.ingest.first.ms").gauge().value()).isGreaterThanOrEqualTo(500);
		assertThat(registry.get("feed.ingest.last.ms").gauge().value()).isLessThan(500);
	}

	@Test
	void theOnlyTagIsTheFeedName() {
		metrics.record(10, System.nanoTime());

		assertThat(registry.getMeters()).hasSize(3);
		assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags()).hasSize(1));
	}
}
