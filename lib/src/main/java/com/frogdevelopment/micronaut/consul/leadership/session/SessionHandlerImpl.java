package com.frogdevelopment.micronaut.consul.leadership.session;

import static java.time.Duration.ZERO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Singleton;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;
import com.frogdevelopment.micronaut.consul.leadership.client.ConsulLeadershipClient;
import com.frogdevelopment.micronaut.consul.leadership.exceptions.NonRecoverableElectionException;

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

    private final AtomicReference<String> sessionIdRef = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> scheduleRef = new AtomicReference<>();

    // @VisibleForTesting
    String getSessionId() {
        return sessionIdRef.get();
    }

    // @VisibleForTesting
    void setSessionId(final String sessionId) {
        this.sessionIdRef.set(sessionId);
    }

    // @VisibleForTesting
    Future<?> getScheduledFuture() {
        return scheduleRef.get();
    }

    // @VisibleForTesting
    void setScheduledFuture(final ScheduledFuture<?> future) {
        this.scheduleRef.set(future);
    }

    @Override
    public Mono<String> createNewSession() {
        return Mono.fromCallable(sessionProvider::createSession)
                .flatMap(client::createSession)
                .map(Session::id)
                .doOnNext(sessionIdRef::set)
                .onErrorResume(error -> Mono.error(new NonRecoverableElectionException("Session creation failed", error)));
    }

    @Override
    public Mono<Void> destroySession() {
        return Mono.justOrEmpty(sessionIdRef.getAndSet(null))
                .flatMap(sessionId -> {
                    log.debug("Destroying session: {}", sessionId);
                    return client.destroySession(sessionId)
                            .onErrorResume(error -> {
                                log.error("Failed to destroy session: {}", sessionId, error);
                                return Mono.empty(); // Continue despite destroy failure
                            });
                });
    }

    @Override
    public Mono<Void> scheduleSessionRenewal() {
        return Mono.fromRunnable(() -> {
            val sessionRenewalDelay = configuration.getElection().getSessionRenewalDelay();
            log.debug("Scheduling session renewal with fixed delay={}", sessionRenewalDelay);
            val scheduledFuture = taskScheduler.scheduleWithFixedDelay(ZERO, sessionRenewalDelay, this::renewSession);
            scheduleRef.set(scheduledFuture);
        });
    }

    // @VisibleForTesting
    void renewSession() {
        final var sessionId = sessionIdRef.get();
        log.debug("Renewing session {}", sessionId);
        client.renewSession(sessionId)
                .onErrorResume(throwable -> {
                    log.error("Failed to renew session, this may lead to leadership loss", throwable);
                    return Mono.empty();
                })
                .timeout(Duration.ofMillis(configuration.getElection().getTimeoutMs())) // Add timeout to prevent hanging
                .subscribeOn(Schedulers.immediate())
                .subscribe();
    }

    @Override
    public Mono<String> cancelSessionRenewal() {
        return Mono.justOrEmpty(scheduleRef.getAndSet(null))
                .doOnNext(schedule -> {
                    log.debug("Cancelling session renewal");
                    final boolean cancelled = schedule.cancel(true);
                    if (!cancelled) {
                        log.warn("Failed to cancel session renewal task");
                    }
                })
                .thenReturn(sessionIdRef.get());
    }

}
