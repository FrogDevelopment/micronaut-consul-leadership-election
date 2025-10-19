package com.frogdevelopment.micronaut.consul.leadership.election;

import reactor.core.publisher.Mono;

public interface LeadershipHandler {

    Mono<Boolean> acquireLeadership(String sessionId);

    Mono<Integer> readLeadershipInfo();

    Mono<Void> releaseLeadership(String sessionId);
}
