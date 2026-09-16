package com.catchmystm.backend.runtime;

import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
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
	
	
}

