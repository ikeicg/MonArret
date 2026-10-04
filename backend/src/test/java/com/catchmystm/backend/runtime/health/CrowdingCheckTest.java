package com.catchmystm.backend.runtime.health;

import static com.catchmystm.backend.runtime.health.TripFixtures.fiveStops;
import static com.catchmystm.backend.runtime.health.TripFixtures.trip;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CrowdingCheckTest {

	private CrowdingLevel level(String occupancy) {
		return CrowdingCheck.evaluate(trip(fiveStops(), List.of(), null, null, occupancy, "SCHEDULED"));
	}

	@Test
	void seatsAvailable_isManySeats() {
		assertThat(level("EMPTY")).isEqualTo(CrowdingLevel.MANY_SEATS);
		assertThat(level("MANY_SEATS_AVAILABLE")).isEqualTo(CrowdingLevel.MANY_SEATS);
	}

	@Test
	void fewSeats_isFewSeats() {
		assertThat(level("FEW_SEATS_AVAILABLE")).isEqualTo(CrowdingLevel.FEW_SEATS);
	}

	@Test
	void standingRoomAndFuller_isCrowded() {
		assertThat(level("STANDING_ROOM_ONLY")).isEqualTo(CrowdingLevel.CROWDED);
		assertThat(level("CRUSHED_STANDING_ROOM_ONLY")).isEqualTo(CrowdingLevel.CROWDED);
		assertThat(level("FULL")).isEqualTo(CrowdingLevel.CROWDED);
	}

	@Test
	void noDataOrAbsent_isUnknown() {
		assertThat(level(null)).isEqualTo(CrowdingLevel.UNKNOWN);
		assertThat(level("NO_DATA_AVAILABLE")).isEqualTo(CrowdingLevel.UNKNOWN);
		assertThat(level("NOT_ACCEPTING_PASSENGERS")).isEqualTo(CrowdingLevel.UNKNOWN);
		assertThat(level("NOT_BOARDABLE")).isEqualTo(CrowdingLevel.UNKNOWN);
	}
}
