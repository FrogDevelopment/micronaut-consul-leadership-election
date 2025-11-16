package com.frogdevelopment.micronaut.consul.leadership.election;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import jakarta.inject.Singleton;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;
import com.frogdevelopment.micronaut.consul.leadership.client.ConsulLeadershipClient;
import com.frogdevelopment.micronaut.consul.leadership.client.KeyValue;
import com.frogdevelopment.micronaut.consul.leadership.event.LeadershipEventsPublisher;
import com.frogdevelopment.micronaut.consul.leadership.exceptions.NonRecoverableElectionException;
import com.frogdevelopment.micronaut.consul.leadership.session.SessionHandler;

import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.scheduling.annotation.Async;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Default implementation of {@link LeaderElectionOrchestrator} using Consul for distributed leadership election.
 * <p>
 * This class implements a robust leader election algorithm using Consul's session-based locking
 * mechanism. It handles the complete lifecycle of leadership election including:
 * </p>
 * <ul>
 *   <li>Session creation and management with automatic renewal</li>
 *   <li>Leadership acquisition and release using Consul KV locks</li>
 *   <li>Monitoring for leadership changes using Consul's blocking queries</li>
 *   <li>Automatic cleanup and failover handling</li>
 *   <li>Error recovery and retry logic</li>
 * </ul>
 * <p>
 * The implementation is thread-safe and uses atomic references to manage state.
 * When leadership is acquired, the instance schedules periodic session renewal
 * to maintain leadership. When leadership is lost or voluntarily released,
 * all associated resources are cleaned up properly.
 * </p>
 * <p>
 * This bean is only created when Consul is available and leadership election
 * is enabled via configuration.
 * </p>
 *
 * @see <a href="https://developer.hashicorp.com/consul/docs/automate/application-leader-election">Consul Application Leader Election</a>
 * @since 1.0.0
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class LeaderElectionOrchestratorImpl implements LeaderElectionOrchestrator {

    private final ConsulLeadershipClient client;
    private final LeadershipConfiguration configuration;
    private final SessionHandler sessionHandler;
    private final LeadershipHandler leadershipHandler;
    private final LeadershipEventsPublisher leadershipEventsPublisher;

    private final AtomicReference<Integer> modifyIndexRef = new AtomicReference<>();
    private final AtomicReference<Disposable> listenerRef = new AtomicReference<>();
    private final AtomicBoolean closingRef = new AtomicBoolean(false);
    private final AtomicLong retryCount = new AtomicLong();

    @Async
    @Override
    public void start() {
        log.debug("Starting Leader Election");
        this.closingRef.set(false);
        this.retryCount.set(0);
        applyForLeadership();
    }

    private void applyForLeadership() {
        log.debug("Applying as leader");
        val mono = sessionHandler.createNewSession()
                .flatMap(leadershipHandler::acquireLeadership)
                .flatMap(result -> Boolean.TRUE.equals(result) ? handleIsLeader() : handleIsNotLeader())
                .doOnError(this::onApplyForLeadershipError);

        watchForLeadershipInfoChanges(mono);
    }

    private Mono<Integer> handleIsLeader() {
        log.info("Leadership acquired successfully");
        // when leader, periodically renew the session to avoid expiration
        return sessionHandler.scheduleSessionRenewal()
                // when acquiring leadership, we updated the KV => index has changed
                .then(Mono.defer(this::readLeadershipInfo));
    }

    private Mono<Integer> readLeadershipInfo() {
        return leadershipHandler.readLeadershipInfo()
                .doOnNext(modifyIndexRef::set);
    }

    private Mono<Integer> handleIsNotLeader() {
        log.info("Leadership acquisition failed, another leader exists");
        return sessionHandler.destroySession()
                .then(Mono.defer(() -> Optional.ofNullable(modifyIndexRef.get())
                        .map(Mono::just)
                        .orElse(readLeadershipInfo())));
    }

    private void onApplyForLeadershipError(final Throwable throwable) {
        log.error("Leadership application failed", throwable);
        onError(throwable, delayed -> delayed.then(Mono.fromRunnable(this::applyForLeadership))
                .subscribeOn(Schedulers.immediate())
                .subscribe());
    }

    // @VisibleForTesting
    void watchForLeadershipInfoChanges(final Mono<Integer> mono) {
        // Ensure we clean up any previous listener
        val previousListener = listenerRef.get();
        if (previousListener != null && !previousListener.isDisposed()) {
            previousListener.dispose();
        }

        val disposable = mono
                .flatMap(currentIndex -> {
                    val path = configuration.getPath();
                    log.debug("Watching for leadership changes on path={} with index={}", path, currentIndex);
                    return client.watchLeadership(path, currentIndex)
                            .doOnError(ReadTimeoutException.class, this::onWatchTimeout)
                            .doOnError(this::onWatchError)
                            .doOnSuccess(this::onLeadershipChanges);
                })
                .subscribeOn(Schedulers.single())
                .subscribe();

        listenerRef.set(disposable);
    }

    private void onWatchTimeout(final Throwable ignored) {
        log.debug("Leadership watch timeout, renewing watcher");
        watchForLeadershipInfoChanges(Mono.just(this.modifyIndexRef.get()));
    }

    private void onWatchError(final Throwable throwable) {
        log.error("Leadership watch failed", throwable);
        onError(throwable, delayed -> watchForLeadershipInfoChanges(delayed.thenReturn(this.modifyIndexRef.get())));
    }

    // @VisibleForTesting
    void onLeadershipChanges(final List<KeyValue> keyValues) {
        if (this.closingRef.get()) {
            log.info("Leadership election is stopping, skipping changes");
            return;
        }
        log.debug("Leadership information changes detected");

        if (CollectionUtils.isEmpty(keyValues)) {
            log.warn("No leadership values found, attempting to apply for leadership");
            applyForLeadership();
            return;
        }

        val kv = keyValues.getFirst();
        this.modifyIndexRef.set(kv.getModifyIndex());

        // If no lock (== no session returned), try to acquire leadership
        if (kv.getSession() == null) {
            log.debug("No active session found, attempting to acquire leadership");
            applyForLeadership();
        } else {
            // Continue watching for changes
            watchForLeadershipInfoChanges(Mono.just(kv.getModifyIndex()));
        }

        leadershipEventsPublisher.publishLeadershipDetailsChange(kv.getValue());
    }

    private void onError(final Throwable throwable, final Consumer<Mono<Void>> consumer) {
        if (throwable instanceof NonRecoverableElectionException) {
            log.error("Non-recoverable error in leadership watch, stopping election participation");
            immediateStop();
        } else {
            final var maxRetries = configuration.getElection().getMaxRetryAttempts();
            final var retry = this.retryCount.incrementAndGet();
            if (retry <= maxRetries) {
                final var duration = calculateRetryDelay(retry);
                log.warn("Recoverable error detected, retrying watch ({}/{}) after delay={}ms", retry, maxRetries, duration.toMillis());
                // Add delay before retrying to avoid hammering the server
                consumer.accept(Mono.delay(duration).then());
            } else {
                log.error("Max retry attempts {} reached, stopping election participation", maxRetries);
                immediateStop();
            }
        }
    }

    /**
     * Calculates retry delay using exponential backoff with jitter.
     * <p>
     * This implementation prevents thundering herd problems by:
     * <ul>
     *   <li>Using exponential backoff (2^retryAttempt)</li>
     *   <li>Capping maximum delay at 30 seconds</li>
     *   <li>Adding random jitter (±25%) to spread out retry attempts</li>
     * </ul>
     *
     * @param retryAttempt the current retry attempt number (1-based)
     * @return the calculated delay duration
     */
    private Duration calculateRetryDelay(final long retryAttempt) {
        final var baseDelayMs = configuration.getElection().getRetryDelayMs();
        final var maxDelayMs = 30000; // 30 seconds cap

        // Exponential backoff: baseDelay * 2^(retryAttempt - 1)
        final long exponentialDelay = (long) (baseDelayMs * Math.pow(2, retryAttempt - 1.0));
        final long cappedDelay = Math.min(exponentialDelay, maxDelayMs);

        // Add jitter (±25%) to prevent thundering herd
        final long maxJitter = (long) (cappedDelay * 0.25);
        final long jitter = maxJitter > 0 ? ThreadLocalRandom.current().nextLong(-maxJitter, maxJitter + 1) : 0;

        return Duration.ofMillis(Math.max(0, cappedDelay + jitter));
    }

    @Blocking
    @Override
    public void stop() {
        doStop()
                .block();
    }

    private void immediateStop() {
        doStop()
                .subscribeOn(Schedulers.immediate())
                .subscribe();
    }

    private Mono<Void> doStop() {
        log.info("Stopping Leader Election");
        this.closingRef.set(true);

        return Mono.justOrEmpty(listenerRef.get())
                .doOnNext(listener -> {
                    log.debug("Stopping leadership watcher");
                    if (!listener.isDisposed()) {
                        listener.dispose();
                    }
                })
                .then(Mono.defer(sessionHandler::cancelSessionRenewal))
                .flatMap(leadershipHandler::releaseLeadership)
                .then(Mono.defer(sessionHandler::destroySession))
                .timeout(Duration.ofMillis(configuration.getElection().getTimeoutMs()))// Add timeout to prevent hanging
                .onErrorResume(throwable -> {
                    log.error("Error during leadership election shutdown", throwable);

                    return Mono.empty();
                })
                .doOnSuccess(ignored -> log.debug("Leader Election shutdown completed"))
                .doFinally(ignored -> {
                    listenerRef.set(null);
                    modifyIndexRef.set(null);
                });
    }

    // @VisibleForTesting
    Disposable getListener() {
        return this.listenerRef.get();
    }

    // @VisibleForTesting
    void setListener(final Disposable disposable) {
        this.listenerRef.set(disposable);
    }

    // @VisibleForTesting
    Integer getModifyIndex() {
        return this.modifyIndexRef.get();
    }

    // @VisibleForTesting
    void setModifyIndex(final Integer modifyIndex) {
        this.modifyIndexRef.set(modifyIndex);
    }

    // @VisibleForTesting
    boolean getClosing() {
        return this.closingRef.get();
    }

    // @VisibleForTesting
    void setClosing(final Boolean stopping) {
        this.closingRef.set(stopping);
    }

}
