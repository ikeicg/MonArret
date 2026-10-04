package com.catchmystm.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.catchmystm.backend.runtime.health.TripHealth;
import com.catchmystm.backend.runtime.health.TripHealthAssembler;
import com.catchmystm.backend.service.FeedHealth;
import com.catchmystm.backend.service.TripHealthService;

@ExtendWith(MockitoExtension.class)
class TripHealthControllerTest {

	private static final String PATH = "/trips/t1/stops/4/health";

	@Mock
	private TripHealthService service;

	private WebTestClient client;

	@BeforeEach
	void setUp() {
		client = WebTestClient.bindToController(new TripHealthController(service)).build();
	}

	private TripHealth health() {
		return TripHealthAssembler.assembleWithoutState("t1", 4, FeedHealth.HEALTHY);
	}

	@Test
	void firstRequest_returnsThePageWithAnETag() {
		when(service.currentVersion()).thenReturn("v1");
		when(service.getTripHealth("t1", 4, "v1")).thenReturn(health());

		client.get().uri(PATH).exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(HttpHeaders.ETAG, "\"v1\"")
				.expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-cache")
				.expectBody()
				.jsonPath("$.success").isEqualTo(true)
				.jsonPath("$.data.tripId").isEqualTo("t1")
				.jsonPath("$.data.stopSequence").isEqualTo(4);
	}

	@Test
	void matchingETag_returns304_withoutBuildingThePage() {
		when(service.currentVersion()).thenReturn("v1");

		client.get().uri(PATH).header(HttpHeaders.IF_NONE_MATCH, "\"v1\"").exchange()
				.expectStatus().isNotModified();

		verify(service, never()).getTripHealth(anyString(), anyInt(), any());
	}

	@Test
	void staleETag_returnsTheNewPage() {
		when(service.currentVersion()).thenReturn("v2");
		when(service.getTripHealth("t1", 4, "v2")).thenReturn(health());

		client.get().uri(PATH).header(HttpHeaders.IF_NONE_MATCH, "\"v1\"").exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(HttpHeaders.ETAG, "\"v2\"");
	}
}
