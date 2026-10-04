package com.catchmystm.backend.runtime.health;

// positionAgeSeconds is set whenever the trip has a position, null otherwise.
public record Headline(HeadlineStatus status, Long positionAgeSeconds) {
}
