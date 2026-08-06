-- V1__gtfs_static_schema.sql
-- GTFS Static Data Schema

CREATE SCHEMA IF NOT EXISTS gtfs_static;

CREATE TABLE gtfs_static.agency (
  agency_id VARCHAR(50) PRIMARY KEY,
  agency_name VARCHAR(255) NOT NULL,
  agency_url VARCHAR(500),
  agency_timezone VARCHAR(50),
  agency_lang VARCHAR(10),
  agency_phone VARCHAR(20),
  agency_fare_url VARCHAR(500),  
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE gtfs_static.calendar (
  service_id VARCHAR(100) PRIMARY KEY,
  monday INT NOT NULL,
  tuesday INT NOT NULL,
  wednesday INT NOT NULL,
  thursday INT NOT NULL,
  friday INT NOT NULL,
  saturday INT NOT NULL,
  sunday INT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_calendar_date_range ON gtfs_static.calendar(start_date, end_date);

CREATE TABLE gtfs_static.calendar_dates (
  service_id VARCHAR(100) NOT NULL REFERENCES gtfs_static.calendar(service_id) ON DELETE CASCADE,
  exception_date DATE NOT NULL,
  exception_type INT NOT NULL,
  PRIMARY KEY (service_id, exception_date),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_calendar_dates_service_id ON gtfs_static.calendar_dates(service_id);
CREATE INDEX idx_calendar_dates_date ON gtfs_static.calendar_dates(exception_date);

CREATE TABLE gtfs_static.stops (
  stop_id VARCHAR(50) PRIMARY KEY,
  stop_code VARCHAR(50),
  stop_name VARCHAR(255) NOT NULL,
  stop_location GEOMETRY(POINT, 4326) NOT NULL,
  stop_url VARCHAR(500),
  location_type INT,
  parent_station VARCHAR(50) REFERENCES gtfs_static.stops(stop_id) ON DELETE SET NULL,
  wheelchair_boarding INT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_stops_name ON gtfs_static.stops(stop_name);
CREATE INDEX idx_stops_location ON gtfs_static.stops USING GIST(stop_location);
CREATE INDEX idx_stops_parent ON gtfs_static.stops(parent_station);

CREATE TABLE gtfs_static.routes (
  route_id VARCHAR(50) PRIMARY KEY,
  agency_id VARCHAR(50) NOT NULL REFERENCES gtfs_static.agency(agency_id) ON DELETE CASCADE,
  route_short_name VARCHAR(50),
  route_long_name VARCHAR(255),
  route_type INT NOT NULL,
  route_url VARCHAR(500),
  route_color VARCHAR(6),
  route_text_color VARCHAR(6),
  route_desc VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_routes_agency_id ON gtfs_static.routes(agency_id);
CREATE INDEX idx_routes_short_name ON gtfs_static.routes(route_short_name);

CREATE TABLE gtfs_static.directions (
  route_direction_id VARCHAR(50) PRIMARY KEY,
  route_id VARCHAR(50) NOT NULL REFERENCES gtfs_static.routes(route_id) ON DELETE CASCADE,
  direction_id INT NOT NULL,
  direction VARCHAR(100),
  direction_legacy VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_directions_route_id ON gtfs_static.directions(route_id);
CREATE UNIQUE INDEX idx_directions_route_direction ON gtfs_static.directions(route_id, direction_id);

CREATE TABLE gtfs_static.route_patterns (
  route_pattern_id VARCHAR(100) PRIMARY KEY,
  route_id VARCHAR(50) NOT NULL REFERENCES gtfs_static.routes(route_id) ON DELETE CASCADE,
  direction_id INT,
  route_pattern_typicality INT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_route_patterns_route_id ON gtfs_static.route_patterns(route_id);

CREATE TABLE gtfs_static.shapes (
  shape_id VARCHAR(100) NOT NULL,
  shape_location GEOMETRY(POINT, 4326) NOT NULL,
  shape_pt_sequence INT NOT NULL,
  route_pattern_id VARCHAR(100) REFERENCES gtfs_static.route_patterns(route_pattern_id) ON DELETE SET NULL,
  PRIMARY KEY (shape_id, shape_pt_sequence),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_shapes_route_pattern ON gtfs_static.shapes(route_pattern_id);

CREATE TABLE gtfs_static.trips (
  trip_id VARCHAR(100) PRIMARY KEY,
  route_id VARCHAR(50) NOT NULL REFERENCES gtfs_static.routes(route_id) ON DELETE CASCADE,
  service_id VARCHAR(100) NOT NULL REFERENCES gtfs_static.calendar(service_id) ON DELETE CASCADE,
  trip_headsign VARCHAR(255),
  direction_id INT,
  shape_id VARCHAR(100),
  wheelchair_accessible INT,
  route_pattern_id VARCHAR(100) REFERENCES gtfs_static.route_patterns(route_pattern_id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_trips_route_id ON gtfs_static.trips(route_id);
CREATE INDEX idx_trips_service_id ON gtfs_static.trips(service_id);
CREATE INDEX idx_trips_route_service ON gtfs_static.trips(route_id, service_id);
CREATE INDEX idx_trips_route_pattern ON gtfs_static.trips(route_pattern_id);

CREATE TABLE gtfs_static.stop_times (
  trip_id VARCHAR(100) NOT NULL REFERENCES gtfs_static.trips(trip_id) ON DELETE CASCADE,
  stop_sequence INT NOT NULL,
  stop_id VARCHAR(50) NOT NULL REFERENCES gtfs_static.stops(stop_id) ON DELETE CASCADE,
  arrival_time INT,
  departure_time INT,
  pickup_type INT,
  PRIMARY KEY (trip_id, stop_sequence),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_stop_times_stop_id ON gtfs_static.stop_times(stop_id);
CREATE INDEX idx_stop_times_trip_id ON gtfs_static.stop_times(trip_id);
CREATE INDEX idx_stop_times_departure ON gtfs_static.stop_times(departure_time);

CREATE TABLE gtfs_static.feed_info (
  id BIGINT NOT NULL PRIMARY KEY,
  feed_publisher_name VARCHAR(255),
  feed_publisher_url VARCHAR(500),
  feed_lang VARCHAR(10),
  feed_start_date DATE,
  feed_end_date DATE,
  feed_version VARCHAR(50),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE gtfs_static.service_dates (
	service_id VARCHAR(100) NOT NULL REFERENCES gtfs_static.calendar(service_id) ON DELETE CASCADE,
	valid_date DATE NOT NULL,
	PRIMARY KEY (service_id, valid_date),
	created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_service_dates_service_id ON gtfs_static.service_dates(service_id);
CREATE INDEX idx_service_dates_valid_date ON gtfs_static.service_dates(valid_date);