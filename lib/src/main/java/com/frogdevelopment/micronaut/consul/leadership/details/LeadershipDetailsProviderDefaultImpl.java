package com.frogdevelopment.micronaut.consul.leadership.details;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.LocalDateTime;

import jakarta.inject.Singleton;

import com.frogdevelopment.micronaut.consul.leadership.exceptions.NonRecoverableElectionException;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.serde.ObjectMapper;

/**
 * Default implementation of {@link LeadershipDetailsProvider} that creates leadership details
 * based on the current application environment and runtime context.
 * <p>
 * This implementation generates leadership details objects containing:
 * </p>
 * <ul>
 *   <li>Hostname - retrieved from the environment configuration or defaults to empty string</li>
 *   <li>Cluster name - retrieved from the "cluster_name" environment property or defaults to empty string</li>
 *   <li>Timestamp - current date/time when leadership is acquired or released</li>
 * </ul>
 * <p>
 * The generated details is stored in Consul's key-value store during leadership
 * acquisition and release operations, allowing other instances to identify the current
 * leader and track leadership transitions.
 * </p>
 * <p>
 * This bean is only instantiated when no other {@link LeadershipDetailsProvider} implementation
 * is available in the application context.
 * </p>
 *
 * @since 1.0.0
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
@Requires(missingBeans = LeadershipDetailsProvider.class)
final class LeadershipDetailsProviderDefaultImpl implements LeadershipDetailsProvider {

    static final String DEFAULT_VALUE = "n/a";

    private final Environment environment;
    private final ObjectMapper objectMapper;

    /**
     * Creates leadership information for the current application instance.
     * <p>
     * This method builds a {@link LeadershipDetailsDefault} object containing the hostname
     * and cluster name from the environment configuration, along with a timestamp
     * indicating when the leadership operation occurred.
     * </p>
     * <p>
     * For leadership acquisition (isAcquire = true), the acquire timestamp is set.
     * For leadership release (isAcquire = false), the release timestamp is set.
     * This allows tracking of leadership transitions in the stored leadership information.
     * </p>
     *
     * @param isAcquire {@code true} if this is for acquiring leadership (sets acquire timestamp),
     *                  {@code false} if this is for releasing leadership (sets release timestamp)
     * @return a {@link LeadershipDetails} object containing hostname, cluster name, and appropriate timestamp
     */
    @Override
    public LeadershipDetails getLeadershipInfo(final boolean isAcquire) {
        final var builder = LeadershipDetailsDefault.builder()
                .hostname(environment.get("hostname", String.class, DEFAULT_VALUE))
                .clusterName(environment.get("cluster_name", String.class, DEFAULT_VALUE));
        if (isAcquire) {
            builder.acquireDateTime(LocalDateTime.now().toString());
        } else {
            builder.releaseDateTime(LocalDateTime.now().toString());
        }
        return builder.build();
    }

    /**
     * Converts a JSON string representation of leadership information into a {@link LeadershipDetails} object.
     * <p>
     * This method deserializes the JSON string retrieved from Consul's key-value store
     * into a {@link LeadershipDetailsDefault} instance. This is used when reading current
     * leader information or watching for leadership changes.
     * </p>
     *
     * @param leadershipInfoValue the JSON string representation of leadership information
     * @return a {@link LeadershipDetails} object deserialized from the JSON string
     * @throws NonRecoverableElectionException if the JSON cannot be parsed or is malformed
     */
    @Override
    public LeadershipDetails convertValue(final String leadershipInfoValue) {
        try {
            log.debug("Current leader information: {}", leadershipInfoValue);
            return objectMapper.readValue(leadershipInfoValue, LeadershipDetailsDefault.class);
        } catch (final IOException e) {
            throw new NonRecoverableElectionException("Unable to process leadershipDetails value " + leadershipInfoValue, e);
        }
    }
}
