package com.frogdevelopment.micronaut.consul.leadership.session;


import lombok.RequiredArgsConstructor;

import java.util.Optional;

import jakarta.inject.Singleton;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;
import com.frogdevelopment.micronaut.consul.leadership.kubernetes.KubernetesInfoResolver;

import io.micronaut.context.env.Environment;

/**
 * Default implementation of {@link SessionProvider} that creates Consul sessions
 * configured for leadership election using application environment and configuration settings.
 * <p>
 * This implementation creates sessions with the following characteristics:
 * </p>
 * <ul>
 *   <li>Session name - FIXME</li>
 *   <li>Session behavior - set to RELEASE, meaning locks are released when the session expires</li>
 *   <li>Lock delay - configured from leadership election settings to prevent rapid lock re-acquisition</li>
 *   <li>TTL (Time To Live) - configured from leadership election settings for session expiration</li>
 * </ul>
 * <p>
 * The session configuration is essential for proper leadership election behavior.
 * The lock delay prevents the "thundering herd" problem when leadership changes,
 * while the TTL ensures that failed instances don't hold locks indefinitely.
 * </p>
 *
 * @since 1.0.0
 */
@Singleton
@RequiredArgsConstructor
final class SessionProviderImpl implements SessionProvider {

    private final Optional<KubernetesInfoResolver> kubernetesInfoResolver;
    private final Environment environment;
    private final LeadershipConfiguration configuration;

    /**
     * Creates a new Consul session configured for leadership election.
     * <p>
     * This method builds a session with configuration appropriate for distributed
     * leadership election. The session includes:
     * </p>
     * <ul>
     *   <li>Name based on the current hostname for identification</li>
     *   <li>RELEASE behavior to automatically release locks when the session expires</li>
     *   <li>Lock delay from configuration to prevent rapid lock transitions</li>
     *   <li>TTL from configuration to control session lifetime</li>
     * </ul>
     * <p>
     * The created session can be used to acquire locks in Consul's key-value store
     * for coordinating leadership election among multiple application instances.
     * </p>
     *
     * @return a new {@link Session} configured with appropriate settings for leadership election
     */
    @Override
    public Session createSession() {
        return Session.builder()
                .name(kubernetesInfoResolver.flatMap(KubernetesInfoResolver::resolvePodName)
                        .or(() -> environment.getProperty("hostname", String.class))
                        .or(() -> environment.getProperty("micronaut.application.name", String.class))
                        .orElseThrow(() -> new IllegalStateException("Neither Pod Name hostname nor application name was resolvable!")))
                .behavior(Session.Behavior.RELEASE)
                .lockDelay(configuration.getElection().getSessionLockDelay())
                .ttl(configuration.getElection().getSessionTtl())
                .build();
    }
}
