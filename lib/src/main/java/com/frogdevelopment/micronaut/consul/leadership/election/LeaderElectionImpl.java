package com.frogdevelopment.micronaut.consul.leadership.election;

import static java.time.Duration.ZERO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;
import com.frogdevelopment.micronaut.consul.leadership.client.ConsulLeadershipClient;
import com.frogdevelopment.micronaut.consul.leadership.client.KeyValue;
import com.frogdevelopment.micronaut.consul.leadership.client.Session;
import com.frogdevelopment.micronaut.consul.leadership.event.LeadershipEventsPublisher;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.consul.condition.RequiresConsul;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import io.micronaut.scheduling.annotation.Async;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Default implementation of {@link LeaderElection} using Consul for distributed leadership election.
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
@RequiresConsul
@RequiredArgsConstructor
@Requires(property = LeadershipConfiguration.PREFIX + ".election.enabled",
          notEquals = StringUtils.FALSE,
          defaultValue = StringUtils.TRUE)
public class LeaderElectionImpl implements LeaderElection {

    private final ConsulLeadershipClient client;
    private final LeadershipConfiguration configuration;
    private final SessionProvider sessionProvider;
    private final LeadershipInfoProvider leadershipInfoProvider;
    @Named(TaskExecutors.SCHEDULED)
    private final TaskScheduler taskScheduler;
    private final LeadershipEventsPublisher leadershipEventsPublisher;

    private final AtomicReference<Integer> modifyIndexRef = new AtomicReference<>();
    private final AtomicReference<Disposable> listenerRef = new AtomicReference<>();
    private final AtomicReference<String> sessionIdRef = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> scheduleRef = new AtomicReference<>();

    private boolean stopping;
    private long retry = 0;

    @Async
    @Override
    public void start() {
        this.stopping = false;
        log.debug("Starting Leader Election");
        applyForLeadership();
    }

    private void applyForLeadership() {
        log.debug("Applying as leader");
        val mono = createNewSession()
                .flatMap(this::acquireLeadership)
                .flatMap(result -> Boolean.TRUE.equals(result) ? handleIsLeader() : handleIsNotLeader());

        watchForLeadershipInfoChanges(mono);
    }

    Mono<Integer> handleIsLeader() {
        log.info("Leadership acquired successfully \uD83C\uDF89 \uD83D\uDC51 \uD83C\uDF89");
        return scheduleSessionRenewal()
                // when acquiring leadership, we updated the KV => index has changed
                .then(Mono.defer(this::readLeadershipInfo));
    }

    Mono<Integer> handleIsNotLeader() {
        log.info("Leadership acquisition failed, another leader exists \uD83D\uDE29 \uD83D\uDE2D");
        return destroySession()
                .then(Mono.defer(() -> this.modifyIndexRef.get() == null ? readLeadershipInfo() : Mono.just(this.modifyIndexRef.get())));
    }

    private Mono<String> createNewSession() {
        return Mono.fromCallable(sessionProvider::createSession)
                .flatMap(client::createSession)
                .map(Session::id)
                .doOnNext(sessionIdRef::set)
                .onErrorResume(error -> Mono.error(new NonRecoverableElectionException("Session creation failed", error)));
    }

    Mono<Boolean> acquireLeadership(final String sessionId) {
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

    // when leader, periodically renew the session to avoid expiration
    private Mono<Void> scheduleSessionRenewal() {
        return Mono.fromRunnable(() -> {
            val sessionRenewalDelay = configuration.getElection().getSessionRenewalDelay();
            log.debug("Scheduling session renewal with fixed delay={}", sessionRenewalDelay);
            val scheduledFuture = taskScheduler.scheduleWithFixedDelay(ZERO, sessionRenewalDelay, this::renewSession);
            scheduleRef.set(scheduledFuture);
        });
    }

    private void renewSession() {
        val sessionId = sessionIdRef.get();
        if (sessionId != null) {
            log.debug("Renewing session: {}", sessionId);
            client.renewSession(sessionId)
                    .onErrorResume(error -> {
                        log.error("Failed to renew session {}, this may lead to leadership loss", sessionId, error);
                        return Mono.error(new RuntimeException("Session renewal failed", error));
                    })
                    .timeout(getTimeout()) // Add timeout to prevent hanging
                    .block();
        } else {
            log.warn("Attempting to renew session without valid session ID");
        }
    }

    private Duration getTimeout() {
        return Duration.ofMillis(configuration.getElection().getTimeoutMs());
    }

    private Mono<Integer> readLeadershipInfo() {
        log.debug("Reading leadership information from path: {}", configuration.getPath());
        return client.readLeadership(configuration.getPath())
                .onErrorResume(error -> Mono.error(new NonRecoverableElectionException("Failed to read leadership information", error)))
                .filter(Predicate.not(List::isEmpty))
                .switchIfEmpty(Mono.error(new NonRecoverableElectionException("No leadership found")))
                .map(List::getFirst)
                .map(keyValue -> {
                    leadershipEventsPublisher.publishLeadershipInfoChange(keyValue.getValue());

                    final var modifyIndex = keyValue.getModifyIndex();
                    this.modifyIndexRef.set(modifyIndex);

                    return modifyIndex;
                });
    }

    private void watchForLeadershipInfoChanges(final Mono<Integer> mono) {
        // Ensure we clean up any previous listener
        val previousListener = listenerRef.get();
        if (previousListener != null && !previousListener.isDisposed()) {
            previousListener.dispose();
        }

        val disposable = mono
                .flatMap(currentIndex -> {
                    val path = configuration.getPath();
                    log.debug("Watching for leadership changes on path={} with index={}", path, currentIndex);
                    return client.watchLeadership(path, currentIndex);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(this::onLeadershipChanges, this::onWatchError);

        listenerRef.set(disposable);
    }

    private void onLeadershipChanges(final List<KeyValue> keyValues) {
        if (this.stopping) {
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
            val currentSessionId = sessionIdRef.get();
            if (kv.getSession().equals(currentSessionId)) {
                log.debug("Leadership confirmed - I am the current leader");
            } else {
                log.debug("Another session holds leadership: {}", kv.getSession());
            }
            // Continue watching for changes
            watchForLeadershipInfoChanges(Mono.just(kv.getModifyIndex()));
        }

        leadershipEventsPublisher.publishLeadershipInfoChange(kv.getValue());
    }

    private void onWatchError(final Throwable throwable) {
        if (throwable instanceof ReadTimeoutException) {
            log.debug("Leadership watch timeout, renewing watcher");
            watchForLeadershipInfoChanges(Mono.just(this.modifyIndexRef.get()));
        } else {
            log.error("Leadership watch failed", throwable);

            if (throwable instanceof NonRecoverableElectionException) {
                log.error("Non-recoverable error in leadership watch, stopping election participation");
                immediateStop();
            } else {
                final var maxRetries = configuration.getElection().getMaxRetryAttempts();
                if (retry < maxRetries) {
                    // todo increase each delay at each error + small random value
                    // read https://medium.com/@kandaanusha/retry-mechanism-50dcad27c0c7
                    final var retryDelayMs = configuration.getElection().getRetryDelayMs();
                    final var duration = Duration.ofMillis(retryDelayMs * ++retry);
                    log.warn("Recoverable error detected, retrying watch ({}/{}) after delay={}ms", retry, maxRetries, duration);
                    // Add delay before retrying to avoid hammering the server
                    watchForLeadershipInfoChanges(Mono.delay(duration)
                            .thenReturn(this.modifyIndexRef.get()));
                } else {
                    log.error("Max retry attempts {} reached, stopping election participation", maxRetries);
                    immediateStop();
                }
            }
        }
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
        this.stopping = true;

        return stopWatching()
                .then(Mono.defer(this::cancelSessionRenewal))
                .then(Mono.defer(this::releaseLeadership))
                .then(Mono.defer(this::destroySession))
                .timeout(getTimeout())// Add timeout to prevent hanging
                .onErrorResume(throwable -> {
                    log.error("Error during leadership election shutdown", throwable);

                    // force clean-up
                    modifyIndexRef.set(null);
                    listenerRef.set(null);
                    scheduleRef.set(null);
                    sessionIdRef.set(null);

                    return Mono.empty();
                });
    }

    private Mono<Void> stopWatching() {
        return Mono.justOrEmpty(listenerRef.getAndSet(null))
                .doOnNext(listener -> {
                    log.debug("Stopping leadership watcher");
                    if (!listener.isDisposed()) {
                        listener.dispose();
                    }
                    modifyIndexRef.set(null);
                })
                .then();
    }

    private Mono<Void> cancelSessionRenewal() {
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

    private Mono<Void> releaseLeadership() {
        return Mono.justOrEmpty(sessionIdRef.get())
                .flatMap(sessionId -> {
                    log.debug("Releasing leadership");
                    return Mono.fromCallable(() -> leadershipInfoProvider.getLeadershipInfo(false))
                            .flatMap(leadershipInfo -> client.releaseLeadership(configuration.getPath(), leadershipInfo, sessionId))
                            .onErrorResume(error -> {
                                log.error("Failed to release leadership gracefully", error);
                                return Mono.empty(); // Continue cleanup despite release failure
                            });
                });
    }

    private Mono<Void> destroySession() {
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
}
