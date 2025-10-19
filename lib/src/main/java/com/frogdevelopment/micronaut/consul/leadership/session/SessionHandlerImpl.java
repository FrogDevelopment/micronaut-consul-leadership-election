package com.frogdevelopment.micronaut.consul.leadership.session;

import static java.time.Duration.ZERO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Singleton;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;
import com.frogdevelopment.micronaut.consul.leadership.client.ConsulLeadershipClient;
import com.frogdevelopment.micronaut.consul.leadership.election.NonRecoverableElectionException;

import io.micronaut.scheduling.TaskScheduler;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SessionHandlerImpl implements SessionHandler {

    private final ConsulLeadershipClient client;
    private final LeadershipConfiguration configuration;
    private final SessionProvider sessionProvider;
    private final TaskScheduler taskScheduler;

    private final AtomicReference<ScheduledFuture<?>> scheduleRef = new AtomicReference<>();

    @Override
    public Mono<String> createNewSession() {
        return Mono.fromCallable(sessionProvider::createSession)
                .flatMap(client::createSession)
                .map(Session::id)
                .onErrorResume(error -> Mono.error(new NonRecoverableElectionException("Session creation failed", error)));
    }

    @Override
    public Mono<Void> destroySession(final String sessionId) {
        log.debug("Destroying session: {}", sessionId);
        return client.destroySession(sessionId)
                .onErrorResume(error -> {
                    log.error("Failed to destroy session: {}", sessionId, error);
                    return Mono.empty(); // Continue despite destroy failure
                });
    }

    @Override
    public Mono<Void> scheduleSessionRenewal(final String sessionId) {
        return Mono.fromRunnable(() -> {
            val sessionRenewalDelay = configuration.getElection().getSessionRenewalDelay();
            log.debug("Scheduling session renewal with fixed delay={}", sessionRenewalDelay);
            val scheduledFuture = taskScheduler.scheduleWithFixedDelay(ZERO, sessionRenewalDelay, () -> renewSession(sessionId));
            scheduleRef.set(scheduledFuture);
        });
    }

    private void renewSession(final String sessionId) {
        log.debug("Renewing session {}", sessionId);
        Mono.justOrEmpty(sessionId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Attempting to renew session without valid session ID")))
                .flatMap(client::renewSession)
                .onErrorResume(throwable -> {
                    log.error("Failed to renew session, this may lead to leadership loss", throwable);
                    return Mono.empty();
                })
                .timeout(Duration.ofMillis(configuration.getElection().getTimeoutMs())) // Add timeout to prevent hanging
                .subscribeOn(Schedulers.immediate())
                .subscribe();
    }

    @Override
    public Mono<Void> cancelSessionRenewal() {
        return Mono.justOrEmpty(scheduleRef.getAndSet(null))
                .doOnNext(schedule -> {
                    log.debug("Cancelling session renewal");
                    final boolean cancelled = schedule.cancel(true);
                    if (!cancelled) {
                        log.warn("Failed to cancel session renewal task");
                    }
                })
                .then();
    }

}
