package com.monarret.backend.runtime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Component;

import com.monarret.backend.runtime.model.TripSnapshot;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ActiveTripStateStore {

    private final ActiveTripStateFactory atsFactory;

	private final Map<String, ActiveTripState> store = new ConcurrentHashMap<>();
	
	public Optional<ActiveTripState> fetchTripState(String tripId) {
		return Optional.ofNullable(store.get(tripId));
	}
	
	// Copies the trip's state under the same per-trip lock the writers use, so the copy is consistent.
	// Never creates a state. Keep the reader short: it runs inside the lock.
	public Optional<TripSnapshot> snapshotTrip(String tripId) {
		AtomicReference<TripSnapshot> snapshot = new AtomicReference<>();
		store.computeIfPresent(tripId, (id, state) -> {
			snapshot.set(state.toSnapshot());
			return state;
		});
		return Optional.ofNullable(snapshot.get());
	}

	public ActiveTripState fetchOrCreateTripState(String tripId, LocalDate serviceDate) {
		return store.compute(tripId, (id, currentState) -> {
			if (isSameRun(currentState, serviceDate)) return currentState;

			ActiveTripState newState = atsFactory.create(id, serviceDate);
			newState.feedTouch();
			return newState;
		});
	}
	
	public void updateTripState(String tripId, LocalDate serviceDate, UnaryOperator<ActiveTripState> updateAction) {
		store.compute(tripId, (id, currentState) -> {
			if (!isSameRun(currentState, serviceDate)) {
				currentState = atsFactory.create(id, serviceDate);
			}

			ActiveTripState updated = updateAction.apply(currentState);
	        if (updated != null) {
	            updated.feedTouch();
	        }
	        return updated;
		});
	}

	
	public void applyTripUpdate(String tripId, LocalDate serviceDate, boolean scheduled, String routeId,
			Integer directionId, UnaryOperator<ActiveTripState> updateAction) {
		store.compute(tripId, (id, currentState) -> {
			if (!isSameRun(currentState, serviceDate)) {
				currentState = scheduled
						? atsFactory.create(id, serviceDate)
						: atsFactory.createBare(id, routeId, directionId, serviceDate);
			} else if (scheduled && currentState.getStopSchedule() == null) {
				currentState = atsFactory.create(id, serviceDate);
			}

			ActiveTripState updated = updateAction.apply(currentState);
			if (updated != null) {
				updated.feedTouch();
			}
			return updated;
		});
	}

	// The same trip_id runs again on later service dates; a state from another date is a different run.
	private boolean isSameRun(ActiveTripState state, LocalDate serviceDate) {
		return state != null && serviceDate.equals(state.getServiceDate());
	}
	
	public void removeTrip(String tripId) {
        store.remove(tripId);
    }
	
	// Staleness is re-checked inside the per-trip lock, so a state touched by the feed meanwhile is kept.
	public int evictStale(Instant cutoff) {
		AtomicInteger evicted = new AtomicInteger();
		for (String tripId : store.keySet()) {
			store.computeIfPresent(tripId, (id, state) -> {
				Instant lastSeen = state.getLastSeenInFeed();
				if (lastSeen != null && lastSeen.isBefore(cutoff)) {
					evicted.incrementAndGet();
					return null;
				}
				return state;
			});
		}
		return evicted.get();
	}

	public int getActiveTripCount() {
        return store.size();
    }
	
	public Collection<ActiveTripState> getAllActiveTrips() {
        return store.values();
    }
	
	public void clearStore() {
		store.clear();
	}
}
