package com.frogdevelopment.micronaut.consul.leadership.election;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;
import com.frogdevelopment.micronaut.consul.leadership.client.ConsulLeadershipClient;
import com.frogdevelopment.micronaut.consul.leadership.client.KeyValue;
import com.frogdevelopment.micronaut.consul.leadership.client.LeadershipInfo;
import com.frogdevelopment.micronaut.consul.leadership.client.Session;
import com.frogdevelopment.micronaut.consul.leadership.event.LeadershipEventsPublisher;

import io.micronaut.scheduling.TaskScheduler;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class LeaderElectionImplTest {

    @Mock
    private ConsulLeadershipClient client;

    @Mock
    private LeadershipConfiguration configuration;

    @Mock
    private LeadershipConfiguration.ElectionConfiguration electionConfiguration;

    @Mock
    private SessionProvider sessionProvider;

    @Mock
    private LeadershipInfoProvider leadershipInfoProvider;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private LeadershipEventsPublisher leadershipEventsPublisher;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    @Mock
    private LeadershipInfo mockLeadershipInfo;

    @Mock
    private KeyValue mockKeyValue;

    @InjectMocks
    private LeaderElectionImpl leaderElection;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;

    @Test
    void handleIsLeader_should_scheduleSessionRenewalThenReadLeadershipInfo() {
        // given
        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getSessionRenewalDelay()).willReturn(Duration.ofSeconds(10));
        given(taskScheduler.scheduleWithFixedDelay(eq(Duration.ZERO), eq(Duration.ofSeconds(10)), runnableCaptor.capture())).will(invocation -> scheduledFuture);

        final var path = "leadership/test-app";
        given(configuration.getPath()).willReturn(path);
        given(client.readLeadership(path)).willReturn(Mono.just(List.of(mockKeyValue)));
        given(mockKeyValue.getModifyIndex()).willReturn(1_234);

        // when
        final var optional = leaderElection.handleIsLeader().blockOptional();

        // then
        assertThat(optional).hasValue(1_234);
        then(client).shouldHaveNoMoreInteractions();
    }

    @Test
    void testStart_successfulLeadershipAcquisition() {
        // Given
        final Session mockSession = createMockSession();
        given(sessionProvider.createSession()).willReturn(mockSession);
        given(client.createSession(mockSession)).willReturn(Mono.just(mockSession));

        given(leadershipInfoProvider.getLeadershipInfo(true)).willReturn(mockLeadershipInfo);
        final var path = "leadership/test-app";
        given(configuration.getPath()).willReturn(path);
        given(client.acquireLeadership(path, mockLeadershipInfo, mockSession.id())).willReturn(Mono.just(true));

        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getSessionRenewalDelay()).willReturn(Duration.ofSeconds(10));
        given(taskScheduler.scheduleWithFixedDelay(eq(Duration.ZERO), eq(Duration.ofSeconds(10)), runnableCaptor.capture())).will((Answer<ScheduledFuture<?>>) invocation -> scheduledFuture);

        given(client.readLeadership(path)).willReturn(Mono.just(List.of(mockKeyValue)));
        given(mockKeyValue.getModifyIndex()).willReturn(1_234);
        given(client.watchLeadership(path, 1_234)).willAnswer(invocation -> {
            waitForAsyncOperations(1000);

            return Mono.just(List.of(mockKeyValue));
        });

        // When
        leaderElection.start();

        // Then
        // Give some time for async operations to complete
        waitForAsyncOperations(200);

        then(sessionProvider).should().createSession();
        then(client).should().createSession(mockSession);
        then(client).should().acquireLeadership(path, mockLeadershipInfo, "test-session-id");

        // Then
        given(client.renewSession(mockSession.id())).willReturn(Mono.empty());
        runnableCaptor.getValue().run();
        then(client).should().renewSession(mockSession.id());
    }

    @Test
    void testStart_failedLeadershipAcquisition() {
        // Given
        final Session mockSession = createMockSession();
        given(sessionProvider.createSession()).willReturn(mockSession);
        given(client.createSession(mockSession)).willReturn(Mono.just(mockSession));

        given(leadershipInfoProvider.getLeadershipInfo(true)).willReturn(mockLeadershipInfo);
        final var path = "leadership/test-app";
        given(configuration.getPath()).willReturn(path);
        given(client.acquireLeadership(path, mockLeadershipInfo, mockSession.id())).willReturn(Mono.just(false));

        given(client.destroySession(mockSession.id())).willReturn(Mono.empty());
        given(client.readLeadership(path)).willReturn(Mono.just(List.of(mockKeyValue)));

        given(client.watchLeadership(path, 0)).willAnswer(invocation -> {
            waitForAsyncOperations(1000);

            return Mono.just(List.of(mockKeyValue));
        });

        // When
        leaderElection.start();

        // Then
        waitForAsyncOperations(200);

        then(client).should().destroySession("test-session-id");
    }

    @Test
    void testStop() {
        // Given - simulate being a leader by setting up the internal state
        final Session mockSession = createMockSession();
        given(sessionProvider.createSession()).willReturn(mockSession);
        given(client.createSession(mockSession)).willReturn(Mono.just(mockSession));

        given(leadershipInfoProvider.getLeadershipInfo(true)).willReturn(mockLeadershipInfo);
        final var path = "leadership/test-app";
        given(configuration.getPath()).willReturn(path);
        given(client.acquireLeadership(path, mockLeadershipInfo, mockSession.id())).willReturn(Mono.just(true));

        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getSessionRenewalDelay()).willReturn(Duration.ofSeconds(10));
        given(electionConfiguration.getTimeoutMs()).willReturn(1000);
        given(taskScheduler.scheduleWithFixedDelay(eq(Duration.ZERO), eq(Duration.ofSeconds(10)), runnableCaptor.capture())).will((Answer<ScheduledFuture<?>>) invocation -> scheduledFuture);

        given(client.readLeadership(path)).willReturn(Mono.just(List.of(mockKeyValue)));
        given(mockKeyValue.getModifyIndex()).willReturn(1_234);

        given(client.watchLeadership(path, 1_234)).willAnswer(invocation -> {
            waitForAsyncOperations(1000);

            return Mono.just(List.of(mockKeyValue));
        });

        given(leadershipInfoProvider.getLeadershipInfo(false)).willReturn(mockLeadershipInfo);
        given(client.releaseLeadership(path, mockLeadershipInfo, mockSession.id())).willReturn(Mono.empty());
        given(client.destroySession(mockSession.id())).willReturn(Mono.empty());
        given(scheduledFuture.cancel(true)).willReturn(true);

        // First start to become a leader
        leaderElection.start();
        waitForAsyncOperations(200);

        // When
        leaderElection.stop();

        // Then
        then(client).should().releaseLeadership(path, mockLeadershipInfo, mockSession.id());
        then(client).should().destroySession(mockSession.id());
        then(scheduledFuture).should().cancel(true);
    }

    @Test
    void testLeadershipWatching() {
        // Given
        final Session mockSession = createMockSession();
        given(sessionProvider.createSession()).willReturn(mockSession);
        given(client.createSession(mockSession)).willReturn(Mono.just(mockSession));

        given(leadershipInfoProvider.getLeadershipInfo(true)).willReturn(mockLeadershipInfo);
        final var path = "leadership/test-app";
        given(configuration.getPath()).willReturn(path);
        given(client.acquireLeadership(path, mockLeadershipInfo, mockSession.id())).willReturn(Mono.just(false));

        given(client.destroySession(mockSession.id())).willReturn(Mono.empty());

        given(client.readLeadership(path)).willReturn(Mono.just(List.of(mockKeyValue)));
        given(mockKeyValue.getModifyIndex()).willReturn(1_234);
        given(mockKeyValue.getSession()).willReturn("other-session-id");

        final var counter = new AtomicInteger(0);
        given(client.watchLeadership(path, 1_234)).willAnswer(invocation -> {
            if (counter.incrementAndGet() == 3) {
                leaderElection.stop();
            }

            waitForAsyncOperations(1000);

            return Mono.just(List.of(mockKeyValue));
        });
        given(mockKeyValue.getSession()).willReturn(mockSession.id());
        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getTimeoutMs()).willReturn(1000);

        // When
        leaderElection.start();
        waitForAsyncOperations(300); // Give more time for watching operations

        // Then
        Awaitility.await().untilAtomic(counter, Matchers.equalTo(3));
        then(client).shouldHaveNoMoreInteractions();
    }

    @Test
    void testErrorsHandling_sessionProvider_createSession() {
        // Given
        given(sessionProvider.createSession())
                .willThrow(new RuntimeException("createSession error"));

        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getTimeoutMs()).willReturn(1000);

        // When
        leaderElection.start();
        waitForAsyncOperations(300);

        // Then
        then(client).shouldHaveNoMoreInteractions();
    }

    @Test
    void testErrorsHandling_client_createSession() {
        // Given
        final Session mockSession = createMockSession();

        given(sessionProvider.createSession()).willReturn(mockSession);
        given(client.createSession(mockSession))
                .willReturn(Mono.error(new RuntimeException("createSession error")));

        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getTimeoutMs()).willReturn(1000);

        // When
        leaderElection.start();
        waitForAsyncOperations(300);

        // Then
        then(client).shouldHaveNoMoreInteractions();
    }

    @Test
    void testErrorsHandling_getLeadershipInfo() {
        // Given
        final Session mockSession = createMockSession();

        given(sessionProvider.createSession()).willReturn(mockSession);
        given(client.createSession(mockSession)).willReturn(Mono.just(mockSession));
        given(leadershipInfoProvider.getLeadershipInfo(true)).willThrow(new RuntimeException("getLeadershipInfo error"));

        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getTimeoutMs()).willReturn(1000);

        given(client.destroySession(any())).willReturn(Mono.empty());

        // When
        leaderElection.start();
        waitForAsyncOperations(500);

        // Then
        then(client).shouldHaveNoMoreInteractions();
    }

    @Test
    @Disabled
    void testErrorsHandling_acquireLeadership() {
        // Given
        final Session mockSession = createMockSession();

        given(sessionProvider.createSession()).willReturn(mockSession);
        given(client.createSession(mockSession)).willReturn(Mono.just(mockSession));
        given(leadershipInfoProvider.getLeadershipInfo(true)).willReturn(mockLeadershipInfo);
        final var path = "leadership/test-app";
        given(configuration.getPath()).willReturn(path);
        given(client.acquireLeadership(path, mockLeadershipInfo, mockSession.id()))
                .willReturn(Mono.error(new RuntimeException("acquireLeadership error")));

        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getTimeoutMs()).willReturn(1000);

        given(client.destroySession(any())).willReturn(Mono.empty());

        // When
        leaderElection.start();
        waitForAsyncOperations(500);

        // Then
        then(client).shouldHaveNoMoreInteractions();
    }

    @Test
    void testErrorsHandling() {
        // Given
        final Session mockSession = createMockSession();

        given(sessionProvider.createSession()).willReturn(mockSession);
        given(client.createSession(mockSession)).willReturn(Mono.just(mockSession));
        given(leadershipInfoProvider.getLeadershipInfo(true)).willReturn(mockLeadershipInfo);
        final var path = "leadership/test-app";
        given(configuration.getPath()).willReturn(path);
        given(client.acquireLeadership(path, mockLeadershipInfo, mockSession.id()))
                .willReturn(Mono.error(new RuntimeException("acquireLeadership error")));

        given(client.destroySession(any()))
                .willReturn(Mono.error(new RuntimeException("destroySession error")));
        given(client.readLeadership(any()))
                .willReturn(Mono.error(new RuntimeException("readLeadership error")));
        given(client.watchLeadership(any(), any()))
                .willReturn(Mono.error(new IllegalStateException("watchLeadership error")));

        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getTimeoutMs()).willReturn(1000);

        // When
        leaderElection.start();
        waitForAsyncOperations(500);

        // Then
        then(client).shouldHaveNoMoreInteractions();
    }

    private Session createMockSession() {
        return Session.builder()
                .id("test-session-id")
                .name("test-name")
                .node("test-node")
                .lockDelay("15s")
                .behavior(Session.Behavior.RELEASE)
                .ttl("30s")
                .nodeChecks(List.of())
                .build();
    }

    private void waitForAsyncOperations(final int millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
