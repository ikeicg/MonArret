package com.catchmystm.backend.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfiguration {

    @Bean
    public RestClient createRestClient() {
        return RestClient.create();
    }

    @Bean
    public Clock applicationClock() {
        return Clock.system(ZoneId.of("America/Montreal"));
    }
}
