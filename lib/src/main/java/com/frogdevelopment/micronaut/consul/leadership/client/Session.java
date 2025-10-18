package com.frogdevelopment.micronaut.consul.leadership.client;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.micronaut.serde.annotation.Serdeable;

@Builder
@Serdeable
@Jacksonized
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public record Session(
        @JsonProperty("ID") String id,
        @JsonProperty("Name") String name,
        @JsonProperty("Node") String node,
        @JsonProperty("LockDelay") String lockDelay,
        @JsonProperty("Behavior") Behavior behavior,
        @JsonProperty("TTL") String ttl,
        @JsonProperty("NodeChecks") List<String> nodeChecks) {

    @Serdeable
    public enum Behavior {
        @JsonProperty("release")
        RELEASE,

        @JsonProperty("delete")
        DELETE
    }

}
