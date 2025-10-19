package com.frogdevelopment.micronaut.consul.leadership.election;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Predicate;

import jakarta.inject.Singleton;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;
import com.frogdevelopment.micronaut.consul.leadership.client.ConsulLeadershipClient;
import com.frogdevelopment.micronaut.consul.leadership.event.LeadershipEventsPublisher;

import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class LeadershipHandler {

    private final ConsulLeadershipClient client;
    private final LeadershipConfiguration configuration;
    private final LeadershipInfoProvider leadershipInfoProvider;
    private final LeadershipEventsPublisher leadershipEventsPublisher;

    public Mono<Boolean> acquireLeadership(final String sessionId) {
        log.debug("Attempting to acquire leadership");

        return Mono.fromCallable(() -> leadershipInfoProvider.getLeadershipInfo(true))
                .onErrorResume(error -> Mono.error(new NonRecoverableElectionException("LeadershipInfo creation failed", error)))
                .flatMap(leadershipInfo -> client.acquireLeadership(configuration.getPath(), leadershipInfo, sessionId)
                        .onErrorResume(error -> {
                            log.error("Leadership acquisition failed", error);
                            return Mono.just(false);
                        }))
                .doOnNext(leadershipEventsPublisher::publishLeadershipChangeEvent);
    }

    public Mono<Integer> readLeadershipInfo() {
        log.debug("Reading leadership information from path: {}", configuration.getPath());
        return client.readLeadership(configuration.getPath())
                .onErrorResume(error -> Mono.error(new NonRecoverableElectionException("Failed to read leadership information", error)))
                .filter(Predicate.not(List::isEmpty))
                .switchIfEmpty(Mono.error(new NonRecoverableElectionException("No leadership found")))
                .map(List::getFirst)
                .map(keyValue -> {
                    leadershipEventsPublisher.publishLeadershipInfoChange(keyValue.getValue());

                    return keyValue.getModifyIndex();
                });
    }

    public Mono<Void> releaseLeadership(final String sessionId) {
        log.debug("Releasing leadership");
        return Mono.fromCallable(() -> leadershipInfoProvider.getLeadershipInfo(false))
                .flatMap(leadershipInfo -> client.releaseLeadership(configuration.getPath(), leadershipInfo, sessionId))
                .onErrorResume(error -> {
                    log.error("Failed to release leadership gracefully", error);
                    return Mono.empty(); // Continue cleanup despite release failure
                })
                .then();
    }

}
