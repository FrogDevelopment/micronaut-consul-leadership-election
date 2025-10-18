package com.frogdevelopment.micronaut.consul.leadership.client;

/**
 * Marker interface for leadership information objects.
 * <p>
 * This interface represents information about the current leader in a distributed
 * leadership election scenario. Implementations of this interface contain details
 * about the leader instance, such as hostname, cluster name, and timestamps of
 * leadership acquisition or release.
 * </p>
 * <p>
 * The leadership information is stored in Consul's key-value store during leadership
 * operations and can be retrieved by all instances to identify the current leader.
 * </p>
 *
 * @see DefaultLeadershipInfo
 * @since 1.0.0
 */
public interface LeadershipInfo {
}
