package com.frogdevelopment.micronaut.consul.leadership.session;

import reactor.core.publisher.Mono;

public interface SessionHandler {

    Mono<String> createNewSession();

    Mono<Void> destroySession();

    Mono<Void> scheduleSessionRenewal();

    Mono<String> cancelSessionRenewal();

}
