package com.frogdevelopment.micronaut.consul.leadership.session;

import reactor.core.publisher.Mono;

public interface SessionHandler {

    Mono<String> createNewSession();

    Mono<Void> destroySession(String sessionId);

    Mono<Void> scheduleSessionRenewal(String sessionId);

    Mono<Void> cancelSessionRenewal();

}
