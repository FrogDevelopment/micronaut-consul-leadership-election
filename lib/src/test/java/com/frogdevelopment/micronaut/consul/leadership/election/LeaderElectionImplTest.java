package com.frogdevelopment.micronaut.consul.leadership.election;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

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
    void testIsLeader_initiallyFalse() {
        // When
        boolean isLeader = leaderElection.isLeader();

        // Then
        assertFalse(isLeader, "Leader election should initially return false");
    }

    @Test
    void testStart_successfulLeadershipAcquisition() {
        // Given
        Session mockSession = createMockSession();
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

        // When
        leaderElection.start();

        // Then
        // Give some time for async operations to complete
        waitForAsyncOperations(200);

        assertTrue(leaderElection.isLeader());
        then(sessionProvider).should().createSession();
        then(client).should().createSession(mockSession);
        then(client).should().acquireLeadership(path, mockLeadershipInfo, "test-session-id");

        runnableCaptor.getValue().run();
        then(client).should().renewSession(mockSession.id());
    }

    @Test
    void testStart_failedLeadershipAcquisition() {
        // Given
        Session mockSession = createMockSession();
        given(sessionProvider.createSession()).willReturn(mockSession);
        given(client.createSession(mockSession)).willReturn(Mono.just(mockSession));

        given(leadershipInfoProvider.getLeadershipInfo(true)).willReturn(mockLeadershipInfo);
        final var path = "leadership/test-app";
        given(configuration.getPath()).willReturn(path);
        given(client.acquireLeadership(path, mockLeadershipInfo, mockSession.id())).willReturn(Mono.just(false));

        given(client.destroySession(mockSession.id())).willReturn(Mono.empty());
        given(client.readLeadership(path)).willReturn(Mono.just(List.of(mockKeyValue)));

        // When
        leaderElection.start();

        // Then
        waitForAsyncOperations(200);

        then(client).should().destroySession("test-session-id");
        assertFalse(leaderElection.isLeader());
    }

    @Test
    void testStart_sessionCreationFailure() {
        // Given
        Session mockSession = createMockSession();

        given(sessionProvider.createSession()).willReturn(mockSession);
        given(client.createSession(mockSession))
                .willReturn(Mono.error(new RuntimeException("Session creation failed")));

        // When
        leaderElection.start();

        // Then
        waitForAsyncOperations(200);

        then(client).should().createSession(mockSession);
        assertFalse(leaderElection.isLeader());
    }

    @Test
    void testStop_whenNotLeader() {
        // When
        leaderElection.stop();

        // Then - should complete without errors
        then(client).shouldHaveNoInteractions();
    }

    @Test
    void testStop_whenLeader() {
        // Given - simulate being a leader by setting up the internal state
        Session mockSession = createMockSession();
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
    @Disabled
    void testLeadershipWatching_emptyKeyValues() {
        // Given
        Session mockSession = createMockSession();
        given(sessionProvider.createSession()).willReturn(mockSession);
        given(client.createSession(mockSession)).willReturn(Mono.just(mockSession));

        given(leadershipInfoProvider.getLeadershipInfo(true)).willReturn(mockLeadershipInfo);
        final var path = "leadership/test-app";
        given(configuration.getPath()).willReturn(path);
        given(client.acquireLeadership(path, mockLeadershipInfo, mockSession.id())).willReturn(Mono.just(false));

        given(client.destroySession(mockSession.id())).willReturn(Mono.empty());

        // 1st calls return empty KV
        // 2d calls return actual data
        given(client.readLeadership(path))
                .willReturn(Mono.just(List.of()))
                .willReturn(Mono.just(List.of(mockKeyValue)));
        given(mockKeyValue.getModifyIndex()).willReturn(1_234);
        given(client.watchLeadership(eq(path), isNull())).willReturn(Mono.just(List.of()));
        given(client.watchLeadership(path, 1_234)).willReturn(Mono.just(List.of(mockKeyValue)));
        given(mockKeyValue.getSession()).willReturn(mockSession.id());

        // When
        leaderElection.start();
        waitForAsyncOperations(300); // Give more time for watching operations

        // Then
        assertFalse(leaderElection.isLeader());
    }

    @Test
    @Disabled
    void testLeadershipWatching_withActiveSession() {
        // Given
        Session mockSession = createMockSession();
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
        given(client.watchLeadership(path, 1_234)).willReturn(Mono.just(List.of(mockKeyValue)));
        given(mockKeyValue.getSession()).willReturn(mockSession.id());

        // When
        leaderElection.start();
        waitForAsyncOperations(300);

        // Then
        assertFalse(leaderElection.isLeader());
    }

    @Test
    void testErrorHandling_acquireLeadershipError() {
        // Given
        Session mockSession = createMockSession();

        given(sessionProvider.createSession()).willReturn(mockSession);
        given(client.createSession(mockSession)).willReturn(Mono.just(mockSession));
        given(leadershipInfoProvider.getLeadershipInfo(true)).willReturn(mockLeadershipInfo);
        final var path = "leadership/test-app";
        given(configuration.getPath()).willReturn(path);
        given(client.acquireLeadership(path, mockLeadershipInfo, mockSession.id()))
                .willReturn(Mono.error(new RuntimeException("Consul error")));

        // When
        leaderElection.start();
        waitForAsyncOperations(300);

        // Then
        assertFalse(leaderElection.isLeader());
    }

    @Test
    void testResourceCleanup_onStop() {
        // Given - set up a scenario where resources need cleanup
        Session mockSession = createMockSession();

        given(sessionProvider.createSession()).willReturn(mockSession);
        given(client.createSession(mockSession)).willReturn(Mono.just(mockSession));
        given(client.destroySession(anyString())).willReturn(Mono.empty());
        given(leadershipInfoProvider.getLeadershipInfo(anyBoolean())).willReturn(mockLeadershipInfo);
        given(client.acquireLeadership(anyString(), any(), anyString()))
                .willReturn(Mono.just(false));
        given(client.readLeadership(anyString())).willReturn(Mono.just(List.of()));

        // Start the election process
        leaderElection.start();
        waitForAsyncOperations(200);

        // When
        leaderElection.stop();

        // Then - verify cleanup operations
        assertFalse(leaderElection.isLeader());
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

    private void waitForAsyncOperations(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
