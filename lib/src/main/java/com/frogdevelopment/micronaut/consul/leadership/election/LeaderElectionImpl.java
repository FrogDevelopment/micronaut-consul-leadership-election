package com.frogdevelopment.micronaut.consul.leadership.election;

import static java.time.Duration.ZERO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import jakarta.inject.Singleton;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;
import com.frogdevelopment.micronaut.consul.leadership.client.ConsulLeadershipClient;
import com.frogdevelopment.micronaut.consul.leadership.client.KeyValue;
import com.frogdevelopment.micronaut.consul.leadership.client.Session;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.consul.condition.RequiresConsul;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.scheduling.TaskScheduler;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

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
// https://developer.hashicorp.com/consul/docs/automate/application-leader-election
@Slf4j
@Singleton
@RequiresConsul
@RequiredArgsConstructor
@Requires(property = LeadershipConfiguration.PREFIX + ".election.enabled",
          notEquals = StringUtils.FALSE,
          defaultValue = StringUtils.FALSE)
public class LeaderElectionImpl implements LeaderElection {

    private final ConsulLeadershipClient client;
    private final LeadershipConfiguration configuration;
    private final SessionProvider sessionProvider;
    private final LeadershipInfoProvider leadershipInfoProvider;
    private final TaskScheduler taskScheduler;

    private final Base64.Decoder base64Decoder = Base64.getDecoder();

    private final AtomicReference<Integer> modifyIndexRef = new AtomicReference<>();
    private final AtomicReference<Disposable> listenerRef = new AtomicReference<>();
    private final AtomicReference<String> sessionIdRef = new AtomicReference<>();
    private final AtomicReference<Boolean> lockAcquireRef = new AtomicReference<>(Boolean.FALSE);
    private final AtomicReference<ScheduledFuture<?>> scheduleRef = new AtomicReference<>();

    private static boolean isNotRecoverableError(Throwable throwable) {
        // fixme Define what constitutes a non-recoverable error
        return throwable instanceof SecurityException ||
               throwable instanceof IllegalStateException;
    }

    @Override
    public boolean isLeader() {
        return lockAcquireRef.get();
    }

    @Override
    public void start() {
        log.debug("Starting Leader Election");
        applyForLeadership();
    }

    private void applyForLeadership() {
        log.debug("Applying as leader");
        val mono = createNewSession()
                .then(Mono.defer(this::acquireLeadership))
                .flatMap(result -> {
                    lockAcquireRef.set(result);
                    return Boolean.TRUE.equals(result) ? handleIsLeader() : handleIsNotLeader();
                })
                .onErrorResume(error -> {
                    log.error("Error during leadership application", error);
                    return handleLeadershipApplicationError(error);
                });

        watchForLeadershipInfoChanges(mono);
    }

    private Mono<Void> handleIsLeader() {
        log.info("Leadership acquired successfully \uD83C\uDF89 \uD83D\uDC51 \uD83C\uDF89");
        return scheduleSessionRenewal()
                // when acquiring leadership, we updated the KV => index has changed
                .then(Mono.defer(this::readLeadershipInfo));
    }

    private Mono<Void> handleIsNotLeader() {
        log.info("Leadership acquisition failed, another leader exists \uD83D\uDE29 \uD83D\uDE2D");
        return destroySession()
                .then(Mono.defer(() -> this.modifyIndexRef.get() == null ? readLeadershipInfo() : Mono.empty()));
    }

    private Mono<Void> createNewSession() {
        val newSession = sessionProvider.createSession();
        log.debug("Creating new session: {}", newSession);
        return client.createSession(newSession)
                .map(Session::id)
                .flatMap(session -> Mono.fromRunnable(() -> {
                    log.debug("Session created: {}", session);
                    sessionIdRef.set(session);
                }))
                .onErrorResume(error -> {
                    log.error("Failed to create session", error);
                    return Mono.error(new RuntimeException("Session creation failed", error));
                })
                .then();
    }

    private Mono<Boolean> acquireLeadership() {
        log.debug("Attempting to acquire leadership");
        val sessionId = sessionIdRef.get();
        if (sessionId == null) {
            log.error("Cannot acquire leadership without a valid session");
            return Mono.error(new IllegalStateException("No valid session available for leadership acquisition"));
        }

        val leadershipInfo = leadershipInfoProvider.getLeadershipInfo(true);
        return client.acquireLeadership(configuration.getPath(), leadershipInfo, sessionId)
                .onErrorResume(error -> {
                    log.error("Leadership acquisition failed", error);
                    return Mono.just(false);
                });
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
                        // fixme Consider implementing retry logic or triggering re-election
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

    private Mono<Void> readLeadershipInfo() {
        log.debug("Reading leadership information from path: {}", configuration.getPath());
        return client.readLeadership(configuration.getPath())
                .filter(Predicate.not(List::isEmpty))
                .flatMap(keyValues -> {
                    val kv = keyValues.getFirst();
                    logCurrentLeadershipInformationsIfEnabled(kv);
                    this.modifyIndexRef.set(kv.getModifyIndex());
                    return Mono.empty();
                })
                .onErrorResume(error -> {
                    log.error("Failed to read leadership information", error);
                    return Mono.empty(); // Continue operation despite read failure
                })
                .then();
    }

    private void logCurrentLeadershipInformationsIfEnabled(final KeyValue keyValue) {
        log.debug("Current modify index: {}", keyValue.getModifyIndex());
        if (log.isDebugEnabled() && keyValue.getValue() != null) {
            try {
                val info = new String(base64Decoder.decode(keyValue.getValue()));
                log.debug("Current leader information: {}", info);
            } catch (Exception e) {
                log.warn("Failed to decode leadership information", e);
            }
        }
    }

    private void watchForLeadershipInfoChanges(final Mono<Void> mono) {
        val disposable = mono
                .then(Mono.defer(() -> {
                    val path = configuration.getPath();
                    val currentIndex = modifyIndexRef.get();
                    log.debug("Watching for leadership changes on path={} with index={}", path, currentIndex);
                    return client.watchLeadership(path, currentIndex);
                }))
                .subscribe(this::onLeadershipChanges, this::onWatchError);

        // Ensure we clean up any previous listener
        val previousListener = listenerRef.getAndSet(disposable);
        if (previousListener != null && !previousListener.isDisposed()) {
            previousListener.dispose();
        }
    }

    private void onLeadershipChanges(final List<KeyValue> keyValues) {
        log.debug("Leadership information changes detected");

        if (CollectionUtils.isEmpty(keyValues)) {
            log.warn("No leadership values found, attempting to apply for leadership");
            applyForLeadership();
            return;
        }

        val kv = keyValues.getFirst();
        modifyIndexRef.set(kv.getModifyIndex());
        logCurrentLeadershipInformationsIfEnabled(kv);

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
            watchForLeadershipInfoChanges(Mono.empty());
        }
    }

    private Mono<Void> handleLeadershipApplicationError(Throwable error) {
        final var retryDelayMs = configuration.getElection().getRetryDelayMs();
        log.warn("Leadership application failed, will retry after {} ms", retryDelayMs, error);
        // todo Schedule retry with exponential backoff would be ideal here
        return Mono.delay(Duration.ofMillis(retryDelayMs))
                .then(Mono.fromRunnable(this::applyForLeadership))
                .then();
    }

    private void onWatchError(final Throwable throwable) {
        if (throwable instanceof ReadTimeoutException) {
            log.debug("Leadership watch timeout, renewing watcher");
            watchForLeadershipInfoChanges(Mono.empty());
        } else {
            log.error("Leadership watch failed", throwable);

            // fixme Implement proper error recovery strategy
            if (isNotRecoverableError(throwable)) {
                log.error("Non-recoverable error in leadership watch, stopping election process");
                stop();
            } else {
                final var retryDelayMs = configuration.getElection().getRetryDelayMs();
                log.info("Recoverable error detected, retrying watch after delay={}ms", retryDelayMs);
                // Add delay before retrying to avoid hammering the server
                watchForLeadershipInfoChanges(Mono.delay(Duration.ofMillis(retryDelayMs)).then());
            }
        }
    }

    @Override
    public void stop() {
        log.info("Stopping Leader Election");

        try {
            if (isLeader()) {
                releaseLeadership()
                        .then(Mono.defer(this::cancelSessionRenewal))
                        .then(Mono.defer(this::destroySession))
                        .then(Mono.defer(this::stopWatching))
                        .timeout(getTimeout()) // Add timeout to prevent hanging
                        .block();
            } else {
                stopWatching()
                        .timeout(getTimeout()) // Add timeout to prevent hanging
                        .block();
            }
        } catch (Exception e) {
            log.error("Error during leadership election shutdown", e);
            // Ensure cleanup even if blocking operations fail
            forceCleanup();
        }
    }

    private void forceCleanup() {
        log.warn("Performing force cleanup of leadership election resources");

        // Clean up references
        val listener = listenerRef.getAndSet(null);
        if (listener != null && !listener.isDisposed()) {
            listener.dispose();
        }

        val schedule = scheduleRef.getAndSet(null);
        if (schedule != null && !schedule.isCancelled()) {
            schedule.cancel(true);
        }

        // Reset state
        lockAcquireRef.set(false);
        sessionIdRef.set(null);
        modifyIndexRef.set(null);
    }

    private Mono<Void> stopWatching() {
        return Mono.justOrEmpty(listenerRef.getAndSet(null))
                .flatMap(listener -> {
                    log.debug("Stopping leadership watcher");
                    if (!listener.isDisposed()) {
                        listener.dispose();
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> releaseLeadership() {
        return Mono.just(lockAcquireRef.getAndSet(false))
                .filter(Predicate.isEqual(Boolean.TRUE))
                .flatMap(ignored -> {
                    log.debug("Releasing leadership");
                    val sessionId = sessionIdRef.get();
                    if (sessionId == null) {
                        log.warn("Cannot release leadership - no session ID available");
                        return Mono.empty();
                    }

                    val leadershipInfo = leadershipInfoProvider.getLeadershipInfo(false);
                    return client.releaseLeadership(configuration.getPath(), leadershipInfo, sessionId)
                            .onErrorResume(error -> {
                                log.error("Failed to release leadership gracefully", error);
                                return Mono.empty(); // Continue cleanup despite release failure
                            });
                });
    }

    private Mono<Void> cancelSessionRenewal() {
        return Mono.justOrEmpty(scheduleRef.getAndSet(null))
                .map(schedule -> {
                    log.debug("Cancelling session renewal");
                    boolean cancelled = schedule.cancel(true);
                    if (!cancelled) {
                        log.warn("Failed to cancel session renewal task");
                    }
                    return cancelled;
                })
                .then();
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
