package com.monarret.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import com.monarret.backend.repository.AppConfigRepository;

@ExtendWith(MockitoExtension.class)
class GtfsStaticLoaderStopTimesTest {

	private static final String HEADER = "trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type\n";

	@Mock
	private RestClient restClient;

	@Mock
	private JdbcTemplate jdbcTemplate;

	@Mock
	private AppConfigRepository appConfigRepository;

	@Mock
	private CacheManager cacheManager;

	private GtfsStaticLoader loader;
	private final List<Integer> batchSizes = new ArrayList<>();

	@BeforeEach
	void setUp() {
		loader = new GtfsStaticLoader(restClient, jdbcTemplate, appConfigRepository, cacheManager);
		// the loader reuses one list between inserts, so the size has to be read at call time
		lenient().doAnswer(call -> {
			batchSizes.add(((Collection<?>) call.getArgument(1)).size());
			return new int[0][];
		}).when(jdbcTemplate).batchUpdate(anyString(), anyCollection(), anyInt(), any());
	}

	@Test
	void insertsEveryRowInChunksNoLargerThanTheChunkSize() throws IOException {
		int rows = 2 * GtfsStaticLoader.STOP_TIME_CHUNK_SIZE + 123;

		int persisted = loader.persistStopTimes(new GeneratedStopTimes(rows));

		assertThat(persisted).isEqualTo(rows);
		assertThat(batchSizes).containsExactly(GtfsStaticLoader.STOP_TIME_CHUNK_SIZE,
				GtfsStaticLoader.STOP_TIME_CHUNK_SIZE, 123);
	}

	@Test
	void skipsMalformedRowsWithoutLosingTheRest() throws IOException {
		String csv = HEADER
				+ "T1,08:00:00,08:00:00,S1,1,0\n"
				+ "not a stop time\n"
				+ "T1,08:05:00,08:05:00,S2,x,0\n"
				+ "T1,08:10:00,08:10:00,S3,3,0\n";

		int persisted = loader.persistStopTimes(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

		assertThat(persisted).isEqualTo(2);
		assertThat(batchSizes).containsExactly(2);
	}

	@Test
	void doesNothingForAFileWithOnlyAHeader() throws IOException {
		int persisted = loader.persistStopTimes(new ByteArrayInputStream(HEADER.getBytes(StandardCharsets.UTF_8)));

		assertThat(persisted).isZero();
		assertThat(batchSizes).isEmpty();
	}

	/** Produces rows only as they are read, so the test never holds the whole file in memory either. */
	private static final class GeneratedStopTimes extends InputStream {
		private final int rows;
		private int produced;
		private byte[] current = HEADER.getBytes(StandardCharsets.UTF_8);
		private int position;

		GeneratedStopTimes(int rows) {
			this.rows = rows;
		}

		@Override
		public int read() {
			while (position >= current.length) {
				if (produced >= rows) {
					return -1;
				}
				current = ("T" + (produced / 20) + ",08:00:00,08:00:00,S" + (produced % 20) + "," + (produced % 20 + 1)
						+ ",0\n").getBytes(StandardCharsets.UTF_8);
				position = 0;
				produced++;
			}
			return current[position++];
		}
	}
}
