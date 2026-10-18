package com.monarret.backend.runtime.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.google.transit.realtime.GtfsRealtime.VehiclePosition;

class OccupancyTest {

	@Test
	void everyFeedValue_mapsToItself() {
		for (VehiclePosition.OccupancyStatus feedValue : VehiclePosition.OccupancyStatus.values()) {
			assertThat(Occupancy.fromFeed(feedValue.name()).name()).isEqualTo(feedValue.name());
		}
	}

	@Test
	void absentOrUnrecognised_isUnknown() {
		assertThat(Occupancy.fromFeed(null)).isEqualTo(Occupancy.UNKNOWN);
		assertThat(Occupancy.fromFeed("SOMETHING_NEW")).isEqualTo(Occupancy.UNKNOWN);
	}
}
