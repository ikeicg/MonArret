package com.monarret.backend.config;

import java.time.Duration;
import java.util.List;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.monarret.backend.dto.ApiResponse;
import com.monarret.backend.dto.DirectionDto;
import com.monarret.backend.dto.RouteDto;
import com.monarret.backend.dto.StopTimeDto;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableCaching
public class RedisConfiguration {

    // Primary, so @Cacheable without a cacheManager keeps using Redis.
    @Bean
    @Primary
    public RedisCacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {

        JavaType routeResponseType =
                objectMapper.getTypeFactory()
                        .constructParametricType(
                                ApiResponse.class,
                                RouteDto.class
                        );

        JavaType directionListType =
                objectMapper.getTypeFactory()
                        .constructCollectionType(
                                List.class,
                                DirectionDto.class
                        );

        JavaType directionResponseType =
                objectMapper.getTypeFactory()
                        .constructParametricType(
                                ApiResponse.class,
                                directionListType
                        );

        JavaType departureListType =
                objectMapper.getTypeFactory()
                        .constructCollectionType(
                                List.class,
                                StopTimeDto.class
                        );

        JacksonJsonRedisSerializer<ApiResponse<RouteDto>> routeSerializer =
                new JacksonJsonRedisSerializer<>(
                        objectMapper,
                        routeResponseType
                );

        JacksonJsonRedisSerializer<ApiResponse<List<DirectionDto>>> directionSerializer =
                new JacksonJsonRedisSerializer<>(
                        objectMapper,
                        directionResponseType
                );

        JacksonJsonRedisSerializer<List<StopTimeDto>> departureSerializer =
                new JacksonJsonRedisSerializer<>(
                        objectMapper,
                        departureListType
                );

        RedisCacheConfiguration oneHour =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofHours(1))
                        .serializeKeysWith(
                        		RedisSerializationContext.SerializationPair
                        			.fromSerializer(new StringRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig())

                .withCacheConfiguration(
                        "routes",
                        oneHour.serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(routeSerializer)
                        )
                )

                .withCacheConfiguration(
                        "route-directions",
                        oneHour.serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(directionSerializer)
                        )
                )

                .withCacheConfiguration(
                        "daily-departures",
                        oneHour.serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(departureSerializer)
                        )
                )

                .build();
    }
}