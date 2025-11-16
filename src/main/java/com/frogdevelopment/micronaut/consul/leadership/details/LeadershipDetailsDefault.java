package com.frogdevelopment.micronaut.consul.leadership.details;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Default implementation of {@link LeadershipDetails} containing standard leadership metadata.
 * <p>
 * This class holds information about the current or past leader in a distributed
 * leadership election scenario. It includes identification details (hostname, cluster name)
 * and temporal information (when leadership was acquired).
 * </p>
 *
 * @since 1.0.0
 */
@Value
@Builder
@Serdeable
@Jacksonized
public class LeadershipDetailsDefault implements LeadershipDetails {

    /**
     * The pod name of the instance holding or having held leadership.
     */
    String podName;

    /**
     * The namespace where the leader instance is deployed.
     */
    String namespace;

    /**
     * The cluster name the leader instance belongs to.
     */
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
