package com.monarret.backend.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import com.monarret.backend.repository.AppConfigRepository;

/** After a new schedule is swapped in, the cached route, direction and departure answers must not outlive it. */
@ExtendWith(MockitoExtension.class)
class GtfsStaticLoaderCacheClearingTest {

	@Mock
	private RestClient restClient;

	@Mock
	private JdbcTemplate jdbcTemplate;

	@Mock
	private AppConfigRepository appConfigRepository;

	@Mock
	private CacheManager cacheManager;

	@Mock
	private Cache routes;

	@Mock
	private Cache routeDirections;

	@Mock
	private Cache dailyDepartures;

	private GtfsStaticLoader loader;

	@BeforeEach
	void setUp() {
		loader = new GtfsStaticLoader(restClient, jdbcTemplate, appConfigRepository, cacheManager);
		lenient().when(cacheManager.getCache("routes")).thenReturn(routes);
		lenient().when(cacheManager.getCache("route-directions")).thenReturn(routeDirections);
		lenient().when(cacheManager.getCache("daily-departures")).thenReturn(dailyDepartures);
	}

	@Test
	void allThreeSearchCachesAreCleared() {
		loader.clearSearchCaches();

		verify(routes).clear();
		verify(routeDirections).clear();
		verify(dailyDepartures).clear();
	}

	@Test
	void aCacheThatFailsToClear_doesNotStopTheOthers() {
		doThrow(new IllegalStateException("redis is down")).when(routes).clear();

		assertThatCode(() -> loader.clearSearchCaches()).doesNotThrowAnyException();

		verify(routeDirections).clear();
		verify(dailyDepartures).clear();
	}

	@Test
	void aCacheThatDoesNotExist_isSkipped() {
		when(cacheManager.getCache("route-directions")).thenReturn(null);

		assertThatCode(() -> loader.clearSearchCaches()).doesNotThrowAnyException();

		verify(routes).clear();
		verify(dailyDepartures).clear();
	}
}
