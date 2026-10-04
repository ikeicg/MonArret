package com.catchmystm.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.catchmystm.backend.runtime.ActiveTripStateStore;
import com.catchmystm.backend.runtime.LiveStateEngine;
import com.catchmystm.backend.runtime.health.HeadlineStatus;
import com.catchmystm.backend.runtime.health.TripHealth;
import com.catchmystm.backend.runtime.model.TripSnapshot;

@ExtendWith(MockitoExtension.class)
class TripHealthServiceTest {

	@Mock
	private ActiveTripStateStore store;

	@Mock
	private StmPoller poller;

	@Mock
	private LiveStateEngine liveStateEngine;

	private TripHealthService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneId.of("America/Montreal"));
		service = new TripHealthService(store, poller, liveStateEngine, clock);
	}

	private TripSnapshot cancelledTrip() {
		return new TripSnapshot("t1", "10", 0, "shape", LocalDate.of(2026, 10, 4), 5000.0, List.of(), List.of(),
				Instant.parse("2026-10-04T11:59:40Z"), null, null, null, null, "CANCELED", false);
	}

	@Test
	void tripWithState_isAssembledFromItsSnapshot() {
		when(poller.getFeedHealth()).thenReturn(FeedHealth.HEALTHY);
		when(store.snapshotTrip("t1")).thenReturn(Optional.of(cancelledTrip()));

		TripHealth health = service.getTripHealth("t1", 4, "v1");

		assertThat(health.routeId()).isEqualTo("10");
		assertThat(health.stopSequence()).isEqualTo(4);
		assertThat(health.headline().status()).isEqualTo(HeadlineStatus.CANCELLED);
	}

	@Test
	void tripWithoutState_healthyFeed_isNoLiveInfo() {
		when(poller.getFeedHealth()).thenReturn(FeedHealth.HEALTHY);
		when(store.snapshotTrip("t1")).thenReturn(Optional.empty());

		TripHealth health = service.getTripHealth("t1", 4, "v1");

		assertThat(health.tripId()).isEqualTo("t1");
		assertThat(health.routeId()).isNull();
		assertThat(health.headline().status()).isEqualTo(HeadlineStatus.NO_LIVE_INFO);
	}

	@Test
	void tripWithoutState_feedDown_isLiveDataUnavailable() {
		when(poller.getFeedHealth()).thenReturn(FeedHealth.DOWN);
		when(store.snapshotTrip("t1")).thenReturn(Optional.empty());

		TripHealth health = service.getTripHealth("t1", 4, "v1");

		assertThat(health.headline().status()).isEqualTo(HeadlineStatus.LIVE_DATA_UNAVAILABLE);
	}

	@Test
	void currentVersion_changesWithTheFeedVersionAndWithFeedHealth() {
		when(liveStateEngine.feedVersion()).thenReturn("100-100", "100-100", "120-120");
		when(poller.getFeedHealth()).thenReturn(FeedHealth.HEALTHY, FeedHealth.DOWN, FeedHealth.DOWN);

		String healthy = service.currentVersion();
		String down = service.currentVersion();
		String nextCycle = service.currentVersion();

		assertThat(down).isNotEqualTo(healthy);
		assertThat(nextCycle).isNotEqualTo(down);
	}
}
