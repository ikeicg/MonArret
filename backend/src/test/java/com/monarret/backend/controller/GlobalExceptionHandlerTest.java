package com.monarret.backend.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.monarret.backend.service.SearchService;

class GlobalExceptionHandlerTest {

	@RestController
	static class FailingController {
		@GetMapping("/boom")
		String boom() {
			throw new IllegalStateException("database password is hunter2");
		}
	}

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders
				.standaloneSetup(new SearchController(mock(SearchService.class)), new FailingController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void missingParameter_isA400WithTheApiResponseShape() throws Exception {
		mvc.perform(get("/search/stop-times"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Missing parameter: routeId"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	void parameterOfTheWrongType_isA400() throws Exception {
		mvc.perform(get("/search/stop-times").param("routeId", "24").param("directionId", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid value for: directionId"));
	}

	@Test
	void wrongMethod_isA405() throws Exception {
		mvc.perform(post("/search/route/24"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Method not allowed"));
	}

	@Test
	void unknownPath_isA404() throws Exception {
		mvc.perform(get("/search/nothing/here"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Not found"));
	}

	@Test
	void unexpectedFailure_isA500ThatRevealsNothing() throws Exception {
		mvc.perform(get("/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Something went wrong"))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hunter2"))));
	}
}
