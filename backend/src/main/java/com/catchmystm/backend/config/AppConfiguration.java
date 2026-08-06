package com.catchmystm.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfiguration {
	
	@Bean
	public RestClient createRestClient() {
		return RestClient.create();
	}

}
