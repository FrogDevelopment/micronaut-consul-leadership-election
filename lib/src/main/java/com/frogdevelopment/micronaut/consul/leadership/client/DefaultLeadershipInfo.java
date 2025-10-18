package com.frogdevelopment.micronaut.consul.leadership.client;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Default implementation of {@link LeadershipInfo} containing standard leadership metadata.
 * <p>
 * This class holds information about the current or past leader in a distributed
 * leadership election scenario. It includes identification details (hostname, cluster name)
 * and temporal information (when leadership was acquired or released).
 * </p>
 * <p>
 * All fields are nullable to accommodate different use cases:
 * </p>
 * <ul>
 *   <li>When acquiring leadership, only acquireDateTime is typically set</li>
 *   <li>When releasing leadership, only releaseDateTime is typically set</li>
 *   <li>When reading current leader info, all fields may be populated</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Value
@Builder
@Serdeable
@Jacksonized
public class DefaultLeadershipInfo implements LeadershipInfo {

    /**
     * The hostname of the instance holding or having held leadership.
     */
    @Nullable
    String hostname;

    /**
     * The cluster name the leader instance belongs to.
     */
    @Nullable
    String clusterName;

    /**
     * The date and time when leadership was acquired, in ISO format.
     */
    @Nullable
    String acquireDateTime;

    /**
     * The date and time when leadership was released, in ISO format.
     */
    @Nullable
    String releaseDateTime;

}
