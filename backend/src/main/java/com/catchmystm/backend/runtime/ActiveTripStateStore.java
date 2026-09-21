package com.catchmystm.backend.runtime;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ActiveTripStateStore {

    private final ActiveTripStateFactory atsFactory;

	private final Map<String, ActiveTripState> store = new ConcurrentHashMap<>();
	
	public Optional<ActiveTripState> fetchTripState(String tripId) {
		return Optional.ofNullable(store.get(tripId));
	}
	
	public ActiveTripState fetchOrCreateTripState(String tripId) {
		return store.computeIfAbsent(tripId, id -> {
            ActiveTripState newState = atsFactory.create(id);
            newState.feedTouch();
            return newState;
        });
	}
	
	public void updateTripState(String tripId, UnaryOperator<ActiveTripState> updateAction) {
		store.compute(tripId, (id, currentState) -> {
			if(currentState == null) {
				currentState = atsFactory.create(id);
			}

			ActiveTripState updated = updateAction.apply(currentState);
	        if (updated != null) {
	            updated.feedTouch();
	        }
	        return updated;
		});
	}

	/**
	 * Trip-update-aware variant: only goes to the database for the full stop schedule/route
	 * geometry when the trip is actually SCHEDULED. A trip that isn't SCHEDULED (e.g. CANCELED)
	 * and isn't already tracked gets a bare state (identifiers only) instead of a wasted DB
	 * round-trip. A previously-bare trip that later turns up SCHEDULED gets upgraded to full.
	 */
	public void applyTripUpdate(String tripId, boolean scheduled, String routeId, Integer directionId,
			UnaryOperator<ActiveTripState> updateAction) {
		store.compute(tripId, (id, currentState) -> {
			if (currentState == null) {
				currentState = scheduled
						? atsFactory.create(id)
						: atsFactory.createBare(id, routeId, directionId);
			} else if (scheduled && currentState.getStopSchedule() == null) {
				currentState = atsFactory.create(id);
			}

			ActiveTripState updated = updateAction.apply(currentState);
			if (updated != null) {
				updated.feedTouch();
			}
			return updated;
		});
	}
	
	public void removeTrip(String tripId) {
        store.remove(tripId);
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
