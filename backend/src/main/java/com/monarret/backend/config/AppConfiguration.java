package com.monarret.backend.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PollProperties.class)
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
