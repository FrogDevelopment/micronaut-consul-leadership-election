package com.frogdevelopment.micronaut.consul.leadership.election;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

import jakarta.inject.Singleton;

import com.frogdevelopment.micronaut.consul.leadership.client.DefaultLeadershipInfo;
import com.frogdevelopment.micronaut.consul.leadership.client.LeadershipInfo;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;

/**
 * Default implementation of {@link LeadershipInfoProvider} that creates leadership information
 * based on the current application environment and runtime context.
 * <p>
 * This implementation generates leadership information objects containing:
 * </p>
 * <ul>
 *   <li>Hostname - retrieved from the environment configuration or defaults to empty string</li>
 *   <li>Cluster name - retrieved from the "cluster_name" environment property or defaults to empty string</li>
 *   <li>Timestamp - current date/time when leadership is acquired or released</li>
 * </ul>
 * <p>
 * The generated information is stored in Consul's key-value store during leadership
 * acquisition and release operations, allowing other instances to identify the current
 * leader and track leadership transitions.
 * </p>
 * <p>
 * This bean is only instantiated when no other {@link LeadershipInfoProvider} implementation
 * is available in the application context.
 * </p>
 *
 * @since 1.0.0
 */
@Singleton
@RequiredArgsConstructor
@Requires(missingBeans = LeadershipInfoProvider.class)
final class DefaultLeadershipInfoProviderImpl implements LeadershipInfoProvider {

    private final Environment environment;

    /**
     * Creates leadership information for the current application instance.
     * <p>
     * This method builds a {@link DefaultLeadershipInfo} object containing the hostname
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
     * @return a {@link LeadershipInfo} object containing hostname, cluster name, and appropriate timestamp
     */
    @Override
    public LeadershipInfo getLeadershipInfo(final boolean isAcquire) {
        final var builder = DefaultLeadershipInfo.builder()
                .hostname(environment.get("hostname", String.class, ""))
                .clusterName(environment.get("cluster_name", String.class, ""));
        if (isAcquire) {
            builder.acquireDateTime(LocalDateTime.now().toString());
        } else {
            builder.releaseDateTime(LocalDateTime.now().toString());
        }
        return builder.build();
    }
}
