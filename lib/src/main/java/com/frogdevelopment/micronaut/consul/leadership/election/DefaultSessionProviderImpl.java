package com.frogdevelopment.micronaut.consul.leadership.election;


import lombok.RequiredArgsConstructor;

import jakarta.inject.Singleton;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;
import com.frogdevelopment.micronaut.consul.leadership.client.Session;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;

@Singleton
@RequiredArgsConstructor
@Requires(missingBeans = SessionProvider.class)
final class DefaultSessionProviderImpl implements SessionProvider {

    private final Environment environment;
    private final LeadershipConfiguration configuration;

    @Override
    public Session createSession() {
        return Session.builder()
                .name(environment.get("hostname", String.class, ""))
                .behavior(Session.Behavior.RELEASE)
                .lockDelay(configuration.getElection().getSessionLockDelay())
                .ttl(configuration.getElection().getSessionTtl())
                .build();
    }
}
