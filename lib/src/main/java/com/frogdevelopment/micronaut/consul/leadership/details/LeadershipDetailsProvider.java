package com.frogdevelopment.micronaut.consul.leadership.details;

import io.micronaut.context.annotation.DefaultImplementation;

/**
 * Provider interface for creating leadership details objects.
 * <p>
 * This interface abstracts the creation of leadership details that gets
 * stored in Consul's key-value store during leadership acquisition and release.
 * The leadership details typically contains details about the current leader
 * instance, such as instance ID, hostname, startup time, and other identifying
 * information.
 * </p>
 * <p>
 * The default implementation provides standard leadership details based on
 * the current application instance and runtime environment.
 * </p>
 *
 * @since 1.0.0
 */
@DefaultImplementation(LeadershipDetailsProviderDefaultImpl.class)
public interface LeadershipDetailsProvider {

    /**
     * Creates leadership information for the current instance.
     * <p>
     * This method generates the leadership information object that will be
     * stored in Consul's key-value store. The content may vary depending on
     * whether this is for acquiring leadership (when the instance becomes leader)
     * or releasing leadership (when the instance steps down).
     * </p>
     *
     * @param isAcquire {@code true} if this is for acquiring leadership,
     *                  {@code false} if this is for releasing leadership
     * @return the leadership information object to store in Consul
     */
    LeadershipDetails getLeadershipInfo(boolean isAcquire);

    LeadershipDetails convertValue(String encodedValue);
}
