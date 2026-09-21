package com.catchmystm.backend;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;

/**
 * Manual diagnostic — hits the live STM GTFS-RT API to capture and print a real
 * feed sample, since STM's protobuf response body isn't human-readable via Swagger.
 * Also saves the raw bytes as a fixture for later WireMock-based tests.
 *
 * Not meant to run as part of the normal suite (needs live network + a valid API
 * key) — run explicitly when refreshing fixtures or inspecting feed shape.
 */
@Disabled("Manual diagnostic only — hits the live STM API; remove @Disabled to re-run explicitly")
class StmLiveFeedInspectionTest {

    private static final String BASE_URL = "https://api.stm.info/pub/od/gtfs-rt/ic/v2";
    private static final String MESSAGES_BASE_URL = "https://api.stm.info/pub/od/i3/v2/messages";
    private static final String API_KEY = System.getenv("STM_API_KEY");

    @Test
    void captureVehiclePositions() throws IOException, InterruptedException {
        capture("/vehiclePositions", "vehicle-positions.pb");
    }

    @Test
    void captureTripUpdates() throws IOException, InterruptedException {
        capture("/tripUpdates", "trip-updates.pb");
    }

    @Test
    void captureServiceAlerts() throws IOException, InterruptedException {
        captureJson(MESSAGES_BASE_URL + "/etatservice", "stm-messages", "etat-service.json");
    }

    private void capture(String endpoint, String fixtureFileName) throws IOException, InterruptedException {
        if (API_KEY == null || API_KEY.isBlank()) {
            throw new IllegalStateException("STM_API_KEY env var not set");
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("apiKey", API_KEY)
                .header("Accept", "application/x-protobuf")
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        System.out.println("=== " + endpoint + " -> HTTP " + response.statusCode() + " ===");

        byte[] body = response.body();
        FeedMessage feedMessage = FeedMessage.parseFrom(body);

        System.out.println("Entity count: " + feedMessage.getEntityCount());
        System.out.println("--- First 2 entities (TextFormat) ---");
        feedMessage.getEntityList().stream().limit(2).forEach(e -> System.out.println(e.toString()));

        Path fixtureDir = Path.of("src/test/resources/fixtures/stm-gtfs-rt");
        Files.createDirectories(fixtureDir);
        Path fixturePath = fixtureDir.resolve(fixtureFileName);
        Files.write(fixturePath, body);
        System.out.println("Saved fixture (" + body.length + " bytes): " + fixturePath.toAbsolutePath());
    }

    private void captureJson(String url, String fixtureSubdir, String fixtureFileName)
            throws IOException, InterruptedException {
        if (API_KEY == null || API_KEY.isBlank()) {
            throw new IllegalStateException("STM_API_KEY env var not set");
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("apiKey", API_KEY)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        System.out.println("=== " + url + " -> HTTP " + response.statusCode() + " ===");

        byte[] body = response.body();

        Path fixtureDir = Path.of("src/test/resources/fixtures/" + fixtureSubdir);
        Files.createDirectories(fixtureDir);
        Path fixturePath = fixtureDir.resolve(fixtureFileName);
        Files.write(fixturePath, body);
        System.out.println("Saved fixture (" + body.length + " bytes): " + fixturePath.toAbsolutePath());
    }
}
