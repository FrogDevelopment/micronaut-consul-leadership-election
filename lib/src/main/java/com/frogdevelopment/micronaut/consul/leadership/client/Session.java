package com.frogdevelopment.micronaut.consul.leadership.client;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Represents a Consul session used for distributed locking and leadership election.
 * <p>
 * Sessions in Consul provide a mechanism to associate locks with a specific client.
 * They have a time-to-live (TTL) and must be periodically renewed. When a session
 * expires or is destroyed, all locks held by that session are automatically released
 * according to the configured behavior.
 * </p>
 *
 * @param id         the unique identifier of the session assigned by Consul
 * @param name       the human-readable name for the session
 * @param node       the Consul node this session is associated with
 * @param lockDelay  the time that must pass before locks can be re-acquired after session destruction
 * @param behavior   the behavior when the session is invalidated (release or delete locks)
 * @param ttl        the time-to-live for the session before automatic expiration
 * @param nodeChecks the list of health checks associated with this session
 * @since 1.0.0
 */
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

    /**
     * Defines the behavior of locks when a session is invalidated.
     */
    @Serdeable
    public enum Behavior {
        /**
         * Release locks when the session is invalidated, making them available for acquisition by other sessions.
         */
        @JsonProperty("release")
        RELEASE,

        /**
         * Delete the key-value entries when the session is invalidated.
         */
        @JsonProperty("delete")
        DELETE
    }

}
