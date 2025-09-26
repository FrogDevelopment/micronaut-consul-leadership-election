package com.frogdevelopment.micronaut.consul.leadership.election;

import com.frogdevelopment.micronaut.consul.leadership.client.Session;

import io.micronaut.context.annotation.DefaultImplementation;

@DefaultImplementation(DefaultSessionProviderImpl.class)
public interface SessionProvider {

    Session createSession();
}
