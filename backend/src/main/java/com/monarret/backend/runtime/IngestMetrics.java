package com.monarret.backend.runtime;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Times the ingestion of one feed's messages. The only tag is the feed name, so it adds a fixed handful of meters.
 * The first duration is kept apart because the first message after startup builds every trip state from the database.
 */
@Slf4j
class IngestMetrics {

	private static final long NONE = -1;

	private final String label;
	private final Timer timer;
	private final AtomicLong lastMillis = new AtomicLong(NONE);
	private final AtomicLong firstMillis = new AtomicLong(NONE);

	IngestMetrics(String feed, String label, MeterRegistry registry) {
		this.label = label;
		this.timer = Timer.builder("feed.ingest")
				.description("Time to ingest one feed message into the trip store")
				.tag("feed", feed)
				.register(registry);
		Gauge.builder("feed.ingest.last.ms", () -> valueOrNaN(lastMillis)).tag("feed", feed).register(registry);
		Gauge.builder("feed.ingest.first.ms", () -> valueOrNaN(firstMillis)).tag("feed", feed).register(registry);
	}

	void record(int entities, long startedAtNanos) {
		long nanos = System.nanoTime() - startedAtNanos;
		long millis = Duration.ofNanos(nanos).toMillis();
		timer.record(Duration.ofNanos(nanos));
		lastMillis.set(millis);
		firstMillis.compareAndSet(NONE, millis);
		log.info("Ingested {} {} entities in {} ms", label, entities, millis);
	}

	long lastMillis() {
		return lastMillis.get();
	}

	long firstMillis() {
		return firstMillis.get();
	}

	private static double valueOrNaN(AtomicLong value) {
		long current = value.get();
		return current == NONE ? Double.NaN : current;
	}
}
