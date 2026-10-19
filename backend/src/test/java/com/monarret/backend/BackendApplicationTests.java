package com.monarret.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Boots the real application against the dev Postgres and Redis, runs the GTFS load check and starts polling STM.
// To be replaced by a smoke test on throwaway containers when CI is set up.
@Disabled("Needs live Postgres and Redis and polls STM; replace with a container-backed smoke test")
@SpringBootTest
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
