package com.monarret.backend.runtime;

import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.stereotype.Service;

@Service
public class GeometricOperationsService {
	
	private final CRSFactory crsFactory = new CRSFactory();

	private final CoordinateReferenceSystem WGS84 = crsFactory.createFromName("epsg:4326");

	private final CoordinateReferenceSystem UTM = crsFactory.createFromName("epsg:2959");

	private final CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();

	private final CoordinateTransform wgsToUtm = ctFactory.createTransform(WGS84, UTM);

	private final GeometryFactory geoFactory = new GeometryFactory();
	
	
	public ProjCoordinate transformWgsToUtm(double longitude, double latitude) {
		
		ProjCoordinate source = new ProjCoordinate(longitude, latitude);
		ProjCoordinate target = new ProjCoordinate();
		
		return wgsToUtm.transform(source, target);
	}
	
	public Point convertProjCoordinateToPoint( ProjCoordinate projection) {
		
		return geoFactory.createPoint(new Coordinate(projection.x, projection.y));
	}
	
	public LineString buildLineStringFromPoint(List<Point> points) {
		
		Coordinate[] coords = points.stream()
										.map(x -> x.getCoordinate())
										.toArray(Coordinate[]::new);
		
		return geoFactory.createLineString(coords);
		
	}
	
	// Meters from the start of the route to the closest point on it; the route must be in UTM like x/y.
	public double distanceAlongRoute(LineString route, double x, double y) {
		return new LengthIndexedLine(route).project(new Coordinate(x, y));
	}
	
	// Meters from the point to the closest point anywhere on the route.
	public double distanceFromRoute(LineString route, double x, double y) {
		return route.distance(geoFactory.createPoint(new Coordinate(x, y)));
	}

	// Same, but only considers the stretch of the route between lo and hi meters (a vehicle between two stops).
	// The result is still measured from the start of the whole route.
	public double distanceAlongRouteBetween(LineString route, double x, double y, double lo, double hi) {
		if (hi - lo < 1e-6) {
			return distanceAlongRoute(route, x, y);
		}
		Geometry stretch = new LengthIndexedLine(route).extractLine(lo, hi);
		return lo + new LengthIndexedLine(stretch).project(new Coordinate(x, y));
	}
	
	// Same, but only considers points on the route at or beyond minDistance (for stops in sequence order).
	public double distanceAlongRouteAfter(LineString route, double x, double y, double minDistance) {
		return new LengthIndexedLine(route).indexOfAfter(new Coordinate(x, y), minDistance);
	}
	
	
}

