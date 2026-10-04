package com.catchmystm.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.catchmystm.backend.runtime.health.TripHealth;
import com.catchmystm.backend.runtime.health.TripHealthAssembler;
import com.catchmystm.backend.service.FeedHealth;
import com.catchmystm.backend.service.TripHealthService;

@ExtendWith(MockitoExtension.class)
class TripHealthControllerTest {

	private static final String PATH = "/trips/t1/stops/4/health";

	@Mock
	private TripHealthService service;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new TripHealthController(service)).build();
	}

	private TripHealth health() {
		return TripHealthAssembler.assembleWithoutState("t1", 4, FeedHealth.HEALTHY);
	}

	@Test
	void firstRequest_returnsThePageWithAnETag() throws Exception {
		when(service.currentVersion()).thenReturn("v1");
		when(service.getTripHealth("t1", 4, "v1")).thenReturn(health());

		mvc.perform(get(PATH))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "\"v1\""))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.tripId").value("t1"))
				.andExpect(jsonPath("$.data.stopSequence").value(4));
	}

	@Test
	void matchingETag_returns304_withoutBuildingThePage() throws Exception {
		when(service.currentVersion()).thenReturn("v1");

		mvc.perform(get(PATH).header(HttpHeaders.IF_NONE_MATCH, "\"v1\""))
				.andExpect(status().isNotModified());

		verify(service, never()).getTripHealth(anyString(), anyInt(), any());
	}

	@Test
	void staleETag_returnsTheNewPage() throws Exception {
		when(service.currentVersion()).thenReturn("v2");
		when(service.getTripHealth("t1", 4, "v2")).thenReturn(health());

		mvc.perform(get(PATH).header(HttpHeaders.IF_NONE_MATCH, "\"v1\""))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ETAG, "\"v2\""));
	}
}
