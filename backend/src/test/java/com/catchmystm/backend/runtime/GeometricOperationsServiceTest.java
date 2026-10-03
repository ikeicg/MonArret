package com.catchmystm.backend.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

class GeometricOperationsServiceTest {

	private static final double EPS = 1e-6;

	private final GeometricOperationsService geo = new GeometricOperationsService();

	// Out and back: outbound leg 0..1000 m, a 10 m connector, return leg 1010..2010 m along the route.
	// The return leg runs 10 m from the outbound leg, so a point between them is close to both passes.
	private final LineString outAndBack = new GeometryFactory().createLineString(new Coordinate[] {
			new Coordinate(0, 0), new Coordinate(1000, 0), new Coordinate(1000, 10), new Coordinate(0, 10) });

	@Test
	void distanceAlongRoute_pointBesideOutboundLeg_isDistanceFromRouteStart() {
		assertThat(geo.distanceAlongRoute(outAndBack, 500, -30)).isCloseTo(500, within(EPS));
	}

	@Test
	void distanceAlongRouteAfter_skipsThePassBeforeTheMinimum() {
		// (500, 5) is 5 m from both legs; with a minimum past the outbound leg only the return pass counts.
		assertThat(geo.distanceAlongRouteAfter(outAndBack, 500, 5, 1010)).isCloseTo(1510, within(EPS));
	}

	@Test
	void distanceAlongRouteBetween_outboundStretch_snapsToOutboundPass() {
		assertThat(geo.distanceAlongRouteBetween(outAndBack, 500, 5, 0, 1000)).isCloseTo(500, within(EPS));
	}

	@Test
	void distanceAlongRouteBetween_returnStretch_snapsToReturnPass_measuredFromRouteStart() {
		assertThat(geo.distanceAlongRouteBetween(outAndBack, 500, 5, 1010, 2010)).isCloseTo(1510, within(EPS));
	}

	@Test
	void distanceAlongRouteBetween_zeroLengthStretch_fallsBackToWholeRoute() {
		assertThat(geo.distanceAlongRouteBetween(outAndBack, 500, -30, 700, 700))
				.isCloseTo(geo.distanceAlongRoute(outAndBack, 500, -30), within(EPS));
	}
}
