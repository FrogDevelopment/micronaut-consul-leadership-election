package com.frogdevelopment.micronaut.consul.leadership.election;

import com.frogdevelopment.micronaut.consul.leadership.client.Session;

import io.micronaut.context.annotation.DefaultImplementation;

/**
 * Provider interface for creating Consul sessions used in leadership election.
 * <p>
 * This interface abstracts the creation of Consul sessions with appropriate
 * configuration for leadership election purposes. Sessions are used to establish
 * locks in Consul's key-value store, and their configuration (TTL, lock delay, etc.)
 * affects the behavior of the leadership election process.
 * </p>
 * <p>
 * The default implementation uses the leadership configuration to create sessions
 * with appropriate settings for distributed leadership election.
 * </p>
 *
 * @since 1.0.0
 */
@DefaultImplementation(DefaultSessionProviderImpl.class)
public interface SessionProvider {

    /**
     * Creates a new Consul session configured for leadership election.
     * <p>
     * The returned session should be properly configured with appropriate
     * TTL, lock delay, and behavior settings that match the leadership
     * election requirements. This session will be used to acquire and
     * maintain locks in Consul's key-value store.
     * </p>
     *
     * @return a new Session configured for leadership election
     */
    Session createSession();
}
