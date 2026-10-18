package com.monarret.backend.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.monarret.backend.runtime.model.GpsPing;
import com.monarret.backend.runtime.model.TripSnapshot;

class ActiveTripStateStoreTest {

	private static final LocalDate DAY = LocalDate.of(2026, 9, 21);
	private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

	private ActiveTripStateFactory factory;
	private ActiveTripStateStore store;

	@BeforeEach
	void setUp() {
		factory = mock(ActiveTripStateFactory.class);
		store = new ActiveTripStateStore(factory);
		when(factory.createBare("t1", "10", 0, DAY)).thenAnswer(i -> bareState());
	}

	@Test
	void snapshot_ofUnknownTrip_isEmpty() {
		assertThat(store.snapshotTrip("nope")).isEmpty();
	}

	@Test
	void snapshot_carriesTheStateFields_andIsEmptyOfStopsWhenBare() {
		store.applyTripUpdate("t1", DAY, false, "10", 0, s -> {
			s.setOccupancyStatus("FEW_SEATS_AVAILABLE");
			s.addGpsPing(1, 2, T0, 50.0, 3.0);
			return s;
		});

		TripSnapshot snap = store.snapshotTrip("t1").orElseThrow();

		assertThat(snap.tripId()).isEqualTo("t1");
		assertThat(snap.routeId()).isEqualTo("10");
		assertThat(snap.stopSchedule()).isEmpty();
		assertThat(snap.occupancyStatus()).isEqualTo("FEW_SEATS_AVAILABLE");
		assertThat(snap.latestPing().alongRoute()).isEqualTo(50.0);
		assertThat(snap.lastSeenInFeed()).isNotNull();
	}

	@Test
	void snapshot_doesNotChangeWhenTheStateChangesAfterwards() {
		store.applyTripUpdate("t1", DAY, false, "10", 0, s -> {
			s.addGpsPing(1, 1, T0, null, null);
			return s;
		});
		TripSnapshot before = store.snapshotTrip("t1").orElseThrow();

		store.updateTripState("t1", DAY, s -> {
			s.addGpsPing(2, 2, T0.plusSeconds(20), null, null);
			s.setOccupancyStatus("FULL");
			return s;
		});

		assertThat(before.recentPings()).hasSize(1);
		assertThat(before.occupancyStatus()).isNull();
		assertThat(store.snapshotTrip("t1").orElseThrow().recentPings()).hasSize(2);
	}

	@Test
	void snapshotLists_areReadOnly() {
		store.applyTripUpdate("t1", DAY, false, "10", 0, s -> s);
		TripSnapshot snap = store.snapshotTrip("t1").orElseThrow();

		assertThatThrownBy(() -> snap.recentPings().add(new GpsPing(0, 0, T0, null, null)))
				.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> snap.stopSchedule().add(null))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	// Safety net, not proof: a race can slip through a run.
	@Test
	void snapshotsTakenWhileAnotherThreadWrites_areAlwaysConsistent() {
		store.applyTripUpdate("t1", DAY, false, "10", 0, s -> s);
		AtomicBoolean writing = new AtomicBoolean(true);

		CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
			for (int i = 1; i <= 20_000; i++) {
				int n = i;
				store.updateTripState("t1", DAY, s -> {
					s.addGpsPing(n, n, T0.plusSeconds(n), (double) n, 0.0);
					// both fields describe the same ping
					s.setCurrentStopSequence(n);
					s.setOccupancyStatus("seq-" + n);
					return s;
				});
			}
			writing.set(false);
		});

		int checked = 0;
		while (writing.get()) {
			TripSnapshot snap = store.snapshotTrip("t1").orElseThrow();
			List<GpsPing> pings = snap.recentPings();
			for (int i = 1; i < pings.size(); i++) {
				assertThat(pings.get(i).timestamp()).isAfter(pings.get(i - 1).timestamp());
			}
			if (snap.latestPing() != null) {
				assertThat(snap.currentStopSequence()).isEqualTo((int) snap.latestPing().x());
				assertThat(snap.occupancyStatus()).isEqualTo("seq-" + snap.currentStopSequence());
			}
			checked++;
		}
		writer.join();

		assertThat(checked).isPositive();
	}

	private ActiveTripState bareState() {
		ActiveTripState s = new ActiveTripState();
		s.setTripId("t1");
		s.setRouteId("10");
		s.setDirectionId(0);
		s.setServiceDate(DAY);
		return s;
	}
}
