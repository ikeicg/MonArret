package com.catchmystm.backend.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.catchmystm.backend.entity.Agency;
import com.catchmystm.backend.entity.AppConfig;
import com.catchmystm.backend.entity.Calendar;
import com.catchmystm.backend.entity.CalendarDate;
import com.catchmystm.backend.entity.CalendarDateId;
import com.catchmystm.backend.entity.Direction;
import com.catchmystm.backend.entity.FeedInfo;
import com.catchmystm.backend.entity.Route;
import com.catchmystm.backend.entity.RoutePattern;
import com.catchmystm.backend.entity.Shape;
import com.catchmystm.backend.entity.ShapeId;
import com.catchmystm.backend.entity.Stop;
import com.catchmystm.backend.entity.StopTime;
import com.catchmystm.backend.entity.StopTimeId;
import com.catchmystm.backend.entity.Trip;
import com.catchmystm.backend.repository.AppConfigRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GtfsStaticLoader implements CommandLineRunner {
    
    @Autowired
    private RestClient restClient;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private AppConfigRepository appConfigRepository;
    
    private static final String STM_GTFS_URL = "https://www.stm.info/sites/default/files/gtfs/gtfs_stm.zip";
    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    
    @Override
    public void run(String... args) {
        if (isReloadDue()) execute();
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "America/Montreal")
    public void scheduledRun() {
        if (isReloadDue()) execute();
    }
   
	@Transactional
    public void execute(String... args) {
        log.info("GTFS static data load started");
        try {
            byte[] zipBytes = downloadGtfsZip();
            Map<String, List<?>> entities = handleGtfsZip(zipBytes);
            
            truncateStagingSchema();
            persistToStagingWithBatch(entities);
            
            if (validateStagingSchema()) {
                swapStagingToStatic();
                
                // Update application based on feed info
                FeedInfo currentFeedInfo = (FeedInfo) entities.get("feed_info").get(0);
                updateAppConfig(currentFeedInfo);
                
                log.info("GTFS static data load completed successfully");
            } else {
                log.error("Staging validation failed, aborting schema swap");
                throw new RuntimeException("Staging schema validation failed");
            }
        } catch (Exception e) {
            log.error("GTFS load failed", e);
            throw new RuntimeException("Failed to load GTFS data", e);
        }
    }
    
    private byte[] downloadGtfsZip() {
        log.info("Downloading GTFS ZIP from STM...");
        byte[] data = restClient.get()
            .uri(STM_GTFS_URL)
            .retrieve()
            .body(byte[].class);
        log.info("Downloaded {} bytes", data.length);
        return data;
    }
    
    private Map<String, List<?>> handleGtfsZip(byte[] zipFile) throws IOException {
        Map<String, List<?>> entities = new HashMap<>();
        
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipFile))) {
            ZipEntry entry;
            
            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();
                log.debug("Processing file: {}", fileName);
                
                switch (fileName) {
                    case "agency.txt":
                        entities.put("agencies", parseAgency(zis));
                        break;
                    case "calendar.txt":
                        entities.put("calendars", parseCalendar(zis));
                        break;
                    case "calendar_dates.txt":
                        entities.put("calendar_dates", parseCalendarDates(zis));
                        break;
                    case "directions.txt":
                        entities.put("directions", parseDirections(zis));
                        break;
                    case "feed_info.txt":
                        entities.put("feed_info", parseFeedInfo(zis));
                        break;
                    case "route_patterns.txt":
                        entities.put("route_patterns", parseRoutePatterns(zis));
                        break;
                    case "routes.txt":
                        entities.put("routes", parseRoutes(zis));
                        break;
                    case "shapes.txt":
                        entities.put("shapes", parseShapes(zis));
                        break;
                    case "stop_times.txt":
                        entities.put("stop_times", parseStopTimes(zis));
                        break;
                    case "stops.txt":
                        entities.put("stops", parseStops(zis));
                        break;
                    case "trips.txt":
                        entities.put("trips", parseTrips(zis));
                        break;
                    default:
                        log.debug("Skipping unknown file: {}", fileName);
                }
                
            }
        }
        
        return entities;
    }
    
    // Parser methods
    
    private List<Agency> parseAgency(ZipInputStream zis) throws IOException {
        List<Agency> agencies = new ArrayList<>();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
        
        reader.readLine();  
        String line;
        int rowCount = 0;
        
        while ((line = reader.readLine()) != null) {
            try {
                String[] fields = line.split(",");
                
                Agency agency = Agency.builder()
                    .agencyId(fields[0].trim())
                    .agencyName(fields[1].trim())
                    .agencyUrl(safeField(fields, 2))
                    .agencyTimezone(safeField(fields, 3))
                    .agencyLang(safeField(fields, 4))
                    .agencyPhone(safeField(fields, 5))
                    .agencyFareUrl(safeField(fields, 6))
                    .createdAt(Instant.now())
                    .build();
                
                agencies.add(agency);
                rowCount++;
            } catch (Exception e) {
                log.warn("Skipping malformed agency row: {}", e.getMessage());
            }
        }
        log.info("Parsed {} agencies", rowCount);
    
        
        return agencies;
    }
    
    private List<Calendar> parseCalendar(ZipInputStream zis) throws IOException {
        List<Calendar> calendars = new ArrayList<>();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
        
        reader.readLine();  
        String line;
        int rowCount = 0;
        
        while ((line = reader.readLine()) != null) {
            try {
                String[] fields = line.split(",");
                                    
                Calendar calendar = Calendar.builder()
                    .serviceId(fields[0].trim())
                    .monday(Integer.parseInt(fields[1].trim()))
                    .tuesday(Integer.parseInt(fields[2].trim()))
                    .wednesday(Integer.parseInt(fields[3].trim()))
                    .thursday(Integer.parseInt(fields[4].trim()))
                    .friday(Integer.parseInt(fields[5].trim()))
                    .saturday(Integer.parseInt(fields[6].trim()))
                    .sunday(Integer.parseInt(fields[7].trim()))
                    .startDate(parseDate(fields[8].trim()))
                    .endDate(parseDate(fields[9].trim()))
                    .createdAt(Instant.now())
                    .build();
                
                calendars.add(calendar);
                rowCount++;
            } catch (Exception e) {
                log.warn("Skipping malformed calendar row: {}", e.getMessage());
            }
        }
        
        log.info("Parsed {} calendars", rowCount);
        
        return calendars;
    }
    
    private List<CalendarDate> parseCalendarDates(ZipInputStream zis) throws IOException {
        List<CalendarDate> calendarDates = new ArrayList<>();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
        reader.readLine();  
        String line;
        int rowCount = 0;
        
        while ((line = reader.readLine()) != null) {
            try {
                String[] fields = line.split(",");
                
                CalendarDateId id = CalendarDateId.builder()
                    .serviceId(fields[0].trim())
                    .exceptionDate(parseDate(fields[1].trim()))
                    .build();
                
                CalendarDate calendarDate = CalendarDate.builder()
                    .id(id)
                    .exceptionType(Integer.parseInt(fields[2].trim()))
                    .createdAt(Instant.now())
                    .build();
                
                calendarDates.add(calendarDate);
                rowCount++;
            } catch (Exception e) {
                log.warn("Skipping malformed calendar_date row: {}", e.getMessage());
            }
        }
        log.info("Parsed {} calendar dates", rowCount);
        
        return calendarDates;
    }
    
    private List<Stop> parseStops(ZipInputStream zis) throws IOException {
        List<Stop> stops = new ArrayList<>();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
        reader.readLine();  
        String line;
        int rowCount = 0;
        
        while ((line = reader.readLine()) != null) {
            try {
                String[] fields = line.split(",");
                
                double lat = Double.parseDouble(fields[3].trim());
                double lon = Double.parseDouble(fields[4].trim());
                Point location = geometryFactory.createPoint(new Coordinate(lon, lat));
                
                Stop stop = Stop.builder()
                    .stopId(fields[0].trim())
                    .stopCode(safeField(fields, 1))
                    .stopName(fields[2].trim())
                    .stopLocation(location)
                    .stopUrl(safeField(fields, 5))
                    .locationType(safeFieldInt(fields, 6))
                    .parentStation(safeField(fields, 7))
                    .wheelchairBoarding(safeFieldInt(fields, 8))
                    .createdAt(Instant.now())
                    .build();
                
                stops.add(stop);
                rowCount++;
            } catch (Exception e) {
                log.warn("Skipping malformed stop row: {}", e.getMessage());
            }
        }
        log.info("Parsed {} stops", rowCount);
        
        return stops;
    }
    
    private List<Route> parseRoutes(ZipInputStream zis) throws IOException {
        List<Route> routes = new ArrayList<>();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
        reader.readLine();  
        String line;
        int rowCount = 0;
        
        while ((line = reader.readLine()) != null) {
            try {
                String[] fields = line.split(",");
                
                Route route = Route.builder()
                    .routeId(fields[0].trim())
                    .agencyId(fields[1].trim())
                    .routeShortName(safeField(fields, 2))
                    .routeLongName(safeField(fields, 3))
                    .routeType(Integer.parseInt(fields[4].trim()))
                    .routeUrl(safeField(fields, 5))
                    .routeColor(safeField(fields, 6))
                    .routeTextColor(safeField(fields, 7))
                    .routeDesc(safeField(fields, 8))
                    .createdAt(Instant.now())
                    .build();
                
                routes.add(route);
                rowCount++;
            } catch (Exception e) {
                log.warn("Skipping malformed route row: {}", e.getMessage());
            }
        }
        log.info("Parsed {} routes", rowCount);
        
        return routes;
    }
    
    private List<Direction> parseDirections(ZipInputStream zis) throws IOException {
        List<Direction> directions = new ArrayList<>();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
        reader.readLine();  
        String line;
        int rowCount = 0;
        
        while ((line = reader.readLine()) != null) {
            try {
                String[] fields = line.split(",");
                
                Direction direction = Direction.builder()
                    .routeDirectionId(fields[0].trim())
                    .routeId(fields[1].trim())
                    .directionId(Integer.parseInt(fields[2].trim()))
                    .direction(safeField(fields, 3))
                    .directionLegacy(safeField(fields, 4))
                    .createdAt(Instant.now())
                    .build();
                
                directions.add(direction);
                rowCount++;
            } catch (Exception e) {
                log.warn("Skipping malformed direction row: {}", e.getMessage());
            }
        }
        log.info("Parsed {} directions", rowCount);
        
        return directions;
    }
    
    private List<RoutePattern> parseRoutePatterns(ZipInputStream zis) throws IOException {
        List<RoutePattern> patterns = new ArrayList<>();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
        reader.readLine();  
        String line;
        int rowCount = 0;
        
        while ((line = reader.readLine()) != null) {
            try {
                String[] fields = line.split(",");
                
                RoutePattern pattern = RoutePattern.builder()
                    .routePatternId(fields[0].trim())
                    .routeId(fields[1].trim())
                    .directionId(safeFieldInt(fields, 2))
                    .routePatternTypicality(safeFieldInt(fields, 3))
                    .createdAt(Instant.now())
                    .build();
                
                patterns.add(pattern);
                rowCount++;
            } catch (Exception e) {
                log.warn("Skipping malformed route_pattern row: {}", e.getMessage());
            }
        }
        log.info("Parsed {} route patterns", rowCount);
        
        return patterns;
    }
    
    private List<Shape> parseShapes(ZipInputStream zis) throws IOException {
        List<Shape> shapes = new ArrayList<>();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
        reader.readLine();  
        String line;
        int rowCount = 0;
        
        while ((line = reader.readLine()) != null) {
            try {
                String[] fields = line.split(",");
                
                double lat = Double.parseDouble(fields[1].trim());
                double lon = Double.parseDouble(fields[2].trim());
                Point location = geometryFactory.createPoint(new Coordinate(lon, lat));
                
                ShapeId id = ShapeId.builder()
                    .shapeId(fields[0].trim())
                    .shapePtSequence(Integer.parseInt(fields[3].trim()))
                    .build();
                
                Shape shape = Shape.builder()
                    .id(id)
                    .shapeLocation(location)
                    .routePatternId(safeField(fields, 4))
                    .createdAt(Instant.now())
                    .build();
                
                shapes.add(shape);
                rowCount++;
            } catch (Exception e) {
                log.warn("Skipping malformed shape row: {}", e.getMessage());
            }
        }
        log.info("Parsed {} shapes", rowCount);
        
        return shapes;
    }
    
    private List<Trip> parseTrips(ZipInputStream zis) throws IOException {
        List<Trip> trips = new ArrayList<>();
        
       BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
        reader.readLine();  
        String line;
        int rowCount = 0;
        
        while ((line = reader.readLine()) != null) {
            try {
                String[] fields = line.split(",");
                
                Trip trip = Trip.builder()
                    .tripId(fields[2].trim())
                    .routeId(fields[0].trim())
                    .serviceId(fields[1].trim())
                    .tripHeadsign(safeField(fields, 3))
                    .directionId(safeFieldInt(fields, 4))
                    .shapeId(safeField(fields, 5))
                    .wheelchairAccessible(safeFieldInt(fields, 6))
                    .routePatternId(safeField(fields, 7))
                    .createdAt(Instant.now())
                    .build();
                
                trips.add(trip);
                rowCount++;
            } catch (Exception e) {
                log.warn("Skipping malformed trip row: {}", e.getMessage());
            }
        }
        log.info("Parsed {} trips", rowCount);
        
        return trips;
    }
    
    private List<StopTime> parseStopTimes(ZipInputStream zis) throws IOException {
        List<StopTime> stopTimes = new ArrayList<>();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
        reader.readLine();  
        String line;
        int rowCount = 0;
        
        while ((line = reader.readLine()) != null) {
            try {
                String[] fields = line.split(",");
                
                StopTimeId id = StopTimeId.builder()
                    .tripId(fields[0].trim())
                    .stopSequence(Integer.parseInt(fields[4].trim()))
                    .build();
                
                StopTime stopTime = StopTime.builder()
                    .id(id)
                    .arrivalTime(parseTime(fields[1]))
                    .departureTime(parseTime(fields[2]))
                    .stopId(safeField(fields, 3))
                    .pickupType(safeFieldInt(fields, 5))
                    .createdAt(Instant.now())
                    .build();
                
                stopTimes.add(stopTime);
                rowCount++;
            } catch (Exception e) {
                log.warn("Skipping malformed stop_time row: {}", e.getMessage());
            }
        }
        log.info("Parsed {} stop times", rowCount);
        
        return stopTimes;
    }
    
    private List<FeedInfo> parseFeedInfo(ZipInputStream zis) throws IOException {
    	List<FeedInfo> feedInfos = new ArrayList<>();
    	FeedInfo feedInfo;
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
        reader.readLine();  
        String line = reader.readLine();
        
        if (line != null) {
            try {
                String[] fields = line.split(",");
                
                feedInfo = FeedInfo.builder()
                    .feedPublisherName(safeField(fields, 0))
                    .feedPublisherUrl(safeField(fields, 1))
                    .feedLang(safeField(fields, 2))
                    .feedStartDate(parseDate(safeField(fields, 3)))
                    .feedEndDate(parseDate(safeField(fields, 4)))
                    .feedVersion(safeField(fields, 5))
                    .createdAt(Instant.now())
                    .build();
                
                log.info("Parsed feed info: version {}", feedInfo.getFeedVersion());
                feedInfos.add(feedInfo);
            } catch (Exception e) {
                log.warn("Failed to parse feed_info: {}", e.getMessage());
            }
        }
        
        return feedInfos;
    }
    
    // Persistence
    
    private void truncateStagingSchema() {
        log.info("Truncating gtfs_staging schema...");
        
        jdbcTemplate.update("SET CONSTRAINTS ALL DEFERRED");
        
        String[] tables = {
            "gtfs_staging.stop_times",
            "gtfs_staging.shapes",
            "gtfs_staging.trips",
            "gtfs_staging.route_patterns",
            "gtfs_staging.directions",
            "gtfs_staging.routes",
            "gtfs_staging.calendar_dates",
            "gtfs_staging.calendar",
            "gtfs_staging.stops",
            "gtfs_staging.agency",
            "gtfs_staging.feed_info",
            "gtfs_staging.service_dates"
        };
        
        for (String table : tables) {
            try {
                jdbcTemplate.update("TRUNCATE TABLE " + table + " CASCADE");
                log.debug("Truncated {}", table);
            } catch (Exception e) {
                log.warn("Failed to truncate {}: {}", table, e.getMessage());
            }
        }
        
        jdbcTemplate.update("SET CONSTRAINTS ALL IMMEDIATE");
        log.info("Staging schema truncation complete");
    }
    
    @Transactional
    private void persistToStagingWithBatch(Map<String, List<?>> entities) {
        log.info("Persisting GTFS entities to gtfs_staging with batch updates");
        
        // Tier 1: Leaf entities
        log.info("Persisting Tier 1 (leaf entities)...");
        persistAgencies((List<Agency>) entities.getOrDefault("agencies", new ArrayList<>()));
        persistCalendars((List<Calendar>) entities.getOrDefault("calendars", new ArrayList<>()));
        persistStops((List<Stop>) entities.getOrDefault("stops", new ArrayList<>()));
        persistFeedInfo((List<FeedInfo>) entities.getOrDefault("feed_info", new ArrayList<>()));
        
        // Tier 2: Depends on Tier 1
        log.info("Persisting Tier 2 (routes, calendar_dates, directions)...");
        persistRoutes((List<Route>) entities.getOrDefault("routes", new ArrayList<>()));
        persistCalendarDates((List<CalendarDate>) entities.getOrDefault("calendar_dates", new ArrayList<>()));
        persistDirections((List<Direction>) entities.getOrDefault("directions", new ArrayList<>()));
        
        // Tier 3: Depends on Tier 2
        log.info("Persisting Tier 3 (route_patterns, trips, shapes)...");
        persistRoutePatterns((List<RoutePattern>) entities.getOrDefault("route_patterns", new ArrayList<>()));
        persistTrips((List<Trip>) entities.getOrDefault("trips", new ArrayList<>()));
        persistShapes((List<Shape>) entities.getOrDefault("shapes", new ArrayList<>()));
        
        // Tier 4: Depends on Tier 3
        log.info("Persisting Tier 4 (stop_times)...");
        persistStopTimes((List<StopTime>) entities.getOrDefault("stop_times", new ArrayList<>()));
        
        log.info("Staging persistence complete");
    }
    
    private void persistAgencies(List<Agency> agencies) {
        String sql = "INSERT INTO gtfs_staging.agency (agency_id, agency_name, agency_url, agency_timezone, agency_lang, agency_phone, agency_fare_url, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, agencies, 100, (ps, agency) -> {
            ps.setString(1, agency.getAgencyId());
            ps.setString(2, agency.getAgencyName());
            ps.setString(3, agency.getAgencyUrl());
            ps.setString(4, agency.getAgencyTimezone());
            ps.setString(5, agency.getAgencyLang());
            ps.setString(6, agency.getAgencyPhone());
            ps.setString(7, agency.getAgencyFareUrl());
            ps.setObject(8, Timestamp.from(agency.getCreatedAt()));
        });
        log.info("Persisted {} agencies", agencies.size());
    }
    
    private void persistCalendars(List<Calendar> calendars) {
        String sql = "INSERT INTO gtfs_staging.calendar (service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday, start_date, end_date, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, calendars, 100, (ps, calendar) -> {
            ps.setString(1, calendar.getServiceId());
            ps.setInt(2, calendar.getMonday());
            ps.setInt(3, calendar.getTuesday());
            ps.setInt(4, calendar.getWednesday());
            ps.setInt(5, calendar.getThursday());
            ps.setInt(6, calendar.getFriday());
            ps.setInt(7, calendar.getSaturday());
            ps.setInt(8, calendar.getSunday());
            ps.setObject(9, calendar.getStartDate());
            ps.setObject(10, calendar.getEndDate());
            ps.setObject(11, Timestamp.from(calendar.getCreatedAt()));
        });
        log.info("Persisted {} calendars", calendars.size());
        
        
        // Normalize services

        sql = """
            INSERT INTO gtfs_staging.service_dates
            (service_id, valid_date)
            VALUES (?, ?)
            ON CONFLICT (service_id, valid_date) DO NOTHING
            """;

        List<Object[]> rows = new ArrayList<>();

        for (Calendar calendar : calendars) {

            LocalDate startDate = calendar.getStartDate();
            LocalDate endDate = calendar.getEndDate();
            String serviceId = calendar.getServiceId();

            Iterable<LocalDate> dates =
                    startDate.datesUntil(endDate.plusDays(1))::iterator;

            for (LocalDate date : dates) {

                boolean valid = switch (date.getDayOfWeek()) {
                    case MONDAY -> calendar.getMonday() == 1;
                    case TUESDAY -> calendar.getTuesday() == 1;
                    case WEDNESDAY -> calendar.getWednesday() == 1;
                    case THURSDAY -> calendar.getThursday() == 1;
                    case FRIDAY -> calendar.getFriday() == 1;
                    case SATURDAY -> calendar.getSaturday() == 1;
                    case SUNDAY -> calendar.getSunday() == 1;
                };

                if (valid) {
                    rows.add(new Object[] {
                        serviceId,
                        date
                    });
                }
            }
        }

        jdbcTemplate.batchUpdate(sql, rows);
    }
    
    private void persistCalendarDates(List<CalendarDate> calendarDates) {
        String sql = "INSERT INTO gtfs_staging.calendar_dates (service_id, exception_date, exception_type, created_at) VALUES (?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, calendarDates, 100, (ps, cd) -> {
            ps.setString(1, cd.getId().getServiceId());
            ps.setObject(2, cd.getId().getExceptionDate());
            ps.setInt(3, cd.getExceptionType());
            ps.setObject(4, Timestamp.from(cd.getCreatedAt()));
        });
        log.info("Persisted {} calendar dates", calendarDates.size());
        
        // Normalize exceptions

        String insertSql = """
            INSERT INTO gtfs_staging.service_dates
            (service_id, valid_date)
            VALUES (?, ?)
            ON CONFLICT (service_id, valid_date) DO NOTHING
            """;

        String deleteSql = """
            DELETE FROM gtfs_staging.service_dates
            WHERE service_id = ?
            AND valid_date = ?
            """;

        List<Object[]> inserts = new ArrayList<>();
        List<Object[]> deletes = new ArrayList<>();

        for (CalendarDate calendarDate : calendarDates) {

            Object[] row = new Object[] {
                calendarDate.getId().getServiceId(),
                calendarDate.getId().getExceptionDate()
            };

            if (calendarDate.getExceptionType() == 1) {
                inserts.add(row);
            } else if (calendarDate.getExceptionType() == 2) {
                deletes.add(row);
            }
        }

        jdbcTemplate.batchUpdate(insertSql, inserts);

        jdbcTemplate.batchUpdate(deleteSql, deletes);
        
    }
    
    private void persistStops(List<Stop> stops) {
        String sql = "INSERT INTO gtfs_staging.stops (stop_id, stop_code, stop_name, stop_location, stop_url, location_type, parent_station, wheelchair_boarding, created_at) VALUES (?, ?, ?, ST_GeomFromText(?, 4326), ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, stops, 1000, (ps, stop) -> {
            ps.setString(1, stop.getStopId());
            ps.setString(2, stop.getStopCode());
            ps.setString(3, stop.getStopName());
            ps.setString(4, stop.getStopLocation().toText());
            ps.setString(5, stop.getStopUrl());
            ps.setObject(6, stop.getLocationType());
            ps.setString(7, stop.getParentStation());
            ps.setObject(8, stop.getWheelchairBoarding());
            ps.setObject(9, Timestamp.from(stop.getCreatedAt()));
        });
        log.info("Persisted {} stops", stops.size());
    }
    
    private void persistRoutes(List<Route> routes) {
        String sql = "INSERT INTO gtfs_staging.routes (route_id, agency_id, route_short_name, route_long_name, route_type, route_url, route_color, route_text_color, route_desc, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, routes, 100, (ps, route) -> {
            ps.setString(1, route.getRouteId());
            ps.setString(2, route.getAgencyId());
            ps.setString(3, route.getRouteShortName());
            ps.setString(4, route.getRouteLongName());
            ps.setInt(5, route.getRouteType());
            ps.setString(6, route.getRouteUrl());
            ps.setString(7, route.getRouteColor());
            ps.setString(8, route.getRouteTextColor());
            ps.setString(9, route.getRouteDesc());
            ps.setObject(10, Timestamp.from(route.getCreatedAt()));
        });
        log.info("Persisted {} routes", routes.size());
    }
    
    private void persistDirections(List<Direction> directions) {
        String sql = "INSERT INTO gtfs_staging.directions (route_direction_id, route_id, direction_id, direction, direction_legacy, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, directions, 100, (ps, direction) -> {
            ps.setString(1, direction.getRouteDirectionId());
            ps.setString(2, direction.getRouteId());
            ps.setInt(3, direction.getDirectionId());
            ps.setString(4, direction.getDirection());
            ps.setString(5, direction.getDirectionLegacy());
            ps.setObject(6, Timestamp.from(direction.getCreatedAt()));
        });
        log.info("Persisted {} directions", directions.size());
    }
    
    private void persistRoutePatterns(List<RoutePattern> patterns) {
        String sql = "INSERT INTO gtfs_staging.route_patterns (route_pattern_id, route_id, direction_id, route_pattern_typicality, created_at) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, patterns, 100, (ps, pattern) -> {
            ps.setString(1, pattern.getRoutePatternId());
            ps.setString(2, pattern.getRouteId());
            ps.setObject(3, pattern.getDirectionId());
            ps.setObject(4, pattern.getRoutePatternTypicality());
            ps.setObject(5, Timestamp.from(pattern.getCreatedAt()));
        });
        log.info("Persisted {} route patterns", patterns.size());
    }
    
    private void persistShapes(List<Shape> shapes) {
        String sql = "INSERT INTO gtfs_staging.shapes (shape_id, shape_location, shape_pt_sequence, route_pattern_id, created_at) VALUES (?, ST_GeomFromText(?, 4326), ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, shapes, 100, (ps, shape) -> {
            ps.setString(1, shape.getId().getShapeId());
            ps.setString(2, shape.getShapeLocation().toText());
            ps.setInt(3, shape.getId().getShapePtSequence());
            ps.setString(4, shape.getRoutePatternId());
            ps.setObject(5, Timestamp.from(shape.getCreatedAt()));
        });
        log.info("Persisted {} shapes", shapes.size());
    }
    
    private void persistTrips(List<Trip> trips) {
        String sql = "INSERT INTO gtfs_staging.trips (trip_id, route_id, service_id, trip_headsign, direction_id, shape_id, wheelchair_accessible, route_pattern_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, trips, 1000, (ps, trip) -> {
            ps.setString(1, trip.getTripId());
            ps.setString(2, trip.getRouteId());
            ps.setString(3, trip.getServiceId());
            ps.setString(4, trip.getTripHeadsign());
            ps.setObject(5, trip.getDirectionId());
            ps.setString(6, trip.getShapeId());
            ps.setObject(7, trip.getWheelchairAccessible());
            ps.setString(8, trip.getRoutePatternId());
            ps.setObject(9, Timestamp.from(trip.getCreatedAt()));
        });
        log.info("Persisted {} trips", trips.size());
    }
    
    private void persistStopTimes(List<StopTime> stopTimes) {
        String sql = "INSERT INTO gtfs_staging.stop_times (trip_id, stop_sequence, stop_id, arrival_time, departure_time, pickup_type, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, stopTimes, 2000, (ps, st) -> {
            ps.setString(1, st.getId().getTripId());
            ps.setInt(2, st.getId().getStopSequence());
            ps.setString(3, st.getStopId());
            ps.setObject(4, st.getArrivalTime());
            ps.setObject(5, st.getDepartureTime());
            ps.setObject(6, st.getPickupType());
            ps.setObject(7, Timestamp.from(st.getCreatedAt()));
        });
        log.info("Persisted {} stop times", stopTimes.size());
    }
    
    private void persistFeedInfo(List<FeedInfo> feedInfos) {
        if (feedInfos.isEmpty()) {
            log.warn("No feed info to persist");
            return;
        }
        String sql = "INSERT INTO gtfs_staging.feed_info (id, feed_publisher_name, feed_publisher_url, feed_lang, feed_start_date, feed_end_date, feed_version, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, feedInfos, 10, (ps, fi) -> {
            ps.setLong(1, 1L);  // Single row, static ID
            ps.setString(2, fi.getFeedPublisherName());
            ps.setString(3, fi.getFeedPublisherUrl());
            ps.setString(4, fi.getFeedLang());
            ps.setObject(5, fi.getFeedStartDate());
            ps.setObject(6, fi.getFeedEndDate());
            ps.setString(7, fi.getFeedVersion());
            ps.setObject(8, Timestamp.from(fi.getCreatedAt()));
        });
        log.info("Persisted {} feed info entries", feedInfos.size());
    }
    
    // Validation
    
    private boolean validateStagingSchema() {
        log.info("Validating gtfs_staging schema...");
        
        try {
            validateTableCounts();
            validateReferentialIntegrity();
            log.info("Staging validation passed");
            return true;
        } catch (Exception e) {
            log.error("Staging validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    private void validateTableCounts() {
        log.info("Validating table row counts...");
        
        String[] checks = {
        	    "SELECT COUNT(*) FROM gtfs_staging.agency",
        	    "SELECT COUNT(*) FROM gtfs_staging.calendar",
        	    "SELECT COUNT(*) FROM gtfs_staging.stops",
        	    "SELECT COUNT(*) FROM gtfs_staging.routes",
        	    "SELECT COUNT(*) FROM gtfs_staging.trips",
        	    "SELECT COUNT(*) FROM gtfs_staging.stop_times",
        	    "SELECT COUNT(*) FROM gtfs_staging.service_dates"
        	};
        
        for (String query : checks) {
            Integer count = jdbcTemplate.queryForObject(query, Integer.class);
            if (count == null || count == 0) {
                throw new RuntimeException("Table validation failed: " + query + " returned 0 rows");
            }
            log.debug("Validation: {} returned {} rows", query, count);
        }
    }
    
    private void validateReferentialIntegrity() {
        log.info("Validating referential integrity...");
        
        Integer orphanRoutes = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gtfs_staging.routes r WHERE NOT EXISTS (SELECT 1 FROM gtfs_staging.agency a WHERE a.agency_id = r.agency_id)",
            Integer.class
        );
        if (orphanRoutes > 0) {
            throw new RuntimeException("Found " + orphanRoutes + " routes with invalid agency_id");
        }
        log.debug("Routes integrity check passed");
        
        Integer orphanTrips = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gtfs_staging.trips t WHERE NOT EXISTS (SELECT 1 FROM gtfs_staging.routes r WHERE r.route_id = t.route_id)",
            Integer.class
        );
        if (orphanTrips > 0) {
            throw new RuntimeException("Found " + orphanTrips + " trips with invalid route_id");
        }
        log.debug("Trips integrity check passed");
        
        Integer orphanServices = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gtfs_staging.trips t WHERE NOT EXISTS (SELECT 1 FROM gtfs_staging.calendar c WHERE c.service_id = t.service_id)",
            Integer.class
        );
        if (orphanServices > 0) {
            throw new RuntimeException("Found " + orphanServices + " trips with invalid service_id");
        }
        log.debug("Service integrity check passed");
        
        Integer orphanStopTimes = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gtfs_staging.stop_times st WHERE NOT EXISTS (SELECT 1 FROM gtfs_staging.trips t WHERE t.trip_id = st.trip_id) OR NOT EXISTS (SELECT 1 FROM gtfs_staging.stops s WHERE s.stop_id = st.stop_id)",
            Integer.class
        );
        if (orphanStopTimes > 0) {
            throw new RuntimeException("Found " + orphanStopTimes + " stop_times with invalid FK references");
        }
        log.debug("Stop times integrity check passed");
    }
    
    // Schema Swap
    
    @Transactional
    private void swapStagingToStatic() {
        log.info("Swapping gtfs_staging to gtfs_static...");
        
        try {
            jdbcTemplate.update("DROP SCHEMA IF EXISTS gtfs_static_old CASCADE");
            jdbcTemplate.update("ALTER SCHEMA gtfs_static RENAME TO gtfs_static_old");
            jdbcTemplate.update("ALTER SCHEMA gtfs_staging RENAME TO gtfs_static");
            jdbcTemplate.update("ALTER SCHEMA gtfs_static_old RENAME TO gtfs_staging");
          
            log.info("Schema swap completed successfully");
        } catch (Exception e) {
            log.error("Schema swap failed, attempting rollback", e);
            try {
                jdbcTemplate.update("ALTER SCHEMA gtfs_static_old RENAME TO gtfs_static");
                log.info("Rollback successful");
            } catch (Exception rollbackEx) {
                log.error("Rollback failed", rollbackEx);
                throw new RuntimeException("Schema swap failed and rollback failed, nawa o", e);
            }
            throw new RuntimeException("Schema swap failed", e);
        }
    }
    
    private void updateAppConfig(FeedInfo feedInfo) {
        log.info("Updating app config after successful GTFS load...");

        AppConfig lastReload = appConfigRepository.findById("gtfs_last_reload")
            .orElseGet(() -> AppConfig.builder().key("gtfs_last_reload").build());
        lastReload.setValue(LocalDate.now().toString());
        appConfigRepository.save(lastReload);

        AppConfig feedVersion = appConfigRepository.findById("gtfs_version")
            .orElseGet(() -> AppConfig.builder().key("gtfs_version").build());
        feedVersion.setValue(feedInfo.getFeedVersion());
        appConfigRepository.save(feedVersion);

        AppConfig nextCheck = appConfigRepository.findById("gtfs_next_check")
            .orElseGet(() -> AppConfig.builder().key("gtfs_next_check").build());
        nextCheck.setValue(feedInfo.getFeedEndDate().plusDays(1).toString());
        appConfigRepository.save(nextCheck);

        log.info("App config updated: version={}, next_check={}", 
            feedInfo.getFeedVersion(), 
            feedInfo.getFeedEndDate().plusDays(1));
    }
    
    // Helper methods
    
    private String safeField(String[] fields, int index) {
        if (index >= fields.length || fields[index].isEmpty()) {
            return null;
        }
        return fields[index].trim();
    }
    
    private Integer safeFieldInt(String[] fields, int index) {
        String value = safeField(fields, index);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private Integer parseTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return null;
        }
        try {
        	String[] p = timeStr.split(":");

        	int totalSeconds =
        	        Integer.parseInt(p[0]) * 3600 +
        	        Integer.parseInt(p[1]) * 60 +
        	        Integer.parseInt(p[2]);
        	return totalSeconds;
        } catch (Exception e) {
            log.warn("Failed to parse time: {}", timeStr);
            return null;
        }
    }
    
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim(), formatter);
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }
    
    // Reload Checker
    
    private boolean isReloadDue() {
        return appConfigRepository.findById("gtfs_next_check")
            .map(config -> {
                LocalDate nextCheck = LocalDate.parse(config.getValue());
                return !nextCheck.isAfter(LocalDate.now());
            })
            .orElse(true);
    }
}