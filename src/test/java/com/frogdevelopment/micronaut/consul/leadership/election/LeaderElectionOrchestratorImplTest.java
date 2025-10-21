package com.frogdevelopment.micronaut.consul.leadership.election;

import static io.micronaut.http.client.exceptions.ReadTimeoutException.TIMEOUT_EXCEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;
import com.frogdevelopment.micronaut.consul.leadership.client.ConsulLeadershipClient;
import com.frogdevelopment.micronaut.consul.leadership.client.KeyValue;
import com.frogdevelopment.micronaut.consul.leadership.event.LeadershipEventsPublisher;
import com.frogdevelopment.micronaut.consul.leadership.exceptions.NonRecoverableElectionException;
import com.frogdevelopment.micronaut.consul.leadership.session.SessionHandler;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class LeaderElectionOrchestratorImplTest {

    @InjectMocks
    private LeaderElectionOrchestratorImpl leaderElectionOrchestrator;

    @Mock
    private ConsulLeadershipClient client;
    @Mock
    private LeadershipConfiguration configuration;
    @Mock
    private SessionHandler sessionHandler;
    @Mock
    private LeadershipHandler leadershipHandler;
    @Mock
    private LeadershipEventsPublisher leadershipEventsPublisher;

    @Mock
    private KeyValue mockedKeyValue;
    @Mock
    private LeadershipConfiguration.ElectionConfiguration electionConfiguration;
    @Mock
    private Disposable disposable;

    @Test
    void start_should_successfulLeadershipAcquisition() {
        // Given
        final var sessionId = "my-session-id";
        given(sessionHandler.createNewSession()).willReturn(Mono.just(sessionId));
        given(leadershipHandler.acquireLeadership(sessionId)).willReturn(Mono.just(true));
        given(sessionHandler.scheduleSessionRenewal()).willReturn(Mono.empty());
        given(leadershipHandler.readLeadershipInfo()).willReturn(Mono.just(1_234));

        final var path = "leadership/test-app";
        given(configuration.getPath()).willReturn(path);
        given(client.watchLeadership(path, 1_234)).willAnswer(invocation -> {
            waitForAsyncOperations(50);

            return Mono.just(List.of(mockedKeyValue));
        });

        // When
        leaderElectionOrchestrator.start();
        // Give some time for async operations to complete
        waitForAsyncOperations();

        // Then
        then(sessionHandler).shouldHaveNoMoreInteractions();
        assertThat(leaderElectionOrchestrator.getClosing()).isFalse();
    }

    @Test
    void start_should_failedLeadershipAcquisition() {
        // Given
        final var sessionId = "my-session-id";
        given(sessionHandler.createNewSession()).willReturn(Mono.just(sessionId));
        given(leadershipHandler.acquireLeadership(sessionId)).willReturn(Mono.just(false));
        given(sessionHandler.destroySession()).willReturn(Mono.empty());
        given(leadershipHandler.readLeadershipInfo()).willReturn(Mono.just(1_234));

        final var path = "leadership/test-app";
        given(configuration.getPath()).willReturn(path);
        given(client.watchLeadership(path, 1_234)).willAnswer(invocation -> {
            waitForAsyncOperations(50);

            return Mono.just(List.of(mockedKeyValue));
        });

        // When
        leaderElectionOrchestrator.start();
        // Give some time for async operations to complete
        waitForAsyncOperations();

        // Then
        then(sessionHandler).shouldHaveNoMoreInteractions();
        assertThat(leaderElectionOrchestrator.getClosing()).isFalse();
    }

    @Test
    void start_should_immediatelyStop_when_NonRecoverableErrorOccurs() {
        // given
        given(sessionHandler.createNewSession()).willReturn(Mono.error(new NonRecoverableElectionException("boom")));

        given(sessionHandler.cancelSessionRenewal()).willReturn(Mono.empty());
        given(sessionHandler.destroySession()).willReturn(Mono.empty());
        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getTimeoutMs()).willReturn(12);

        // when
        leaderElectionOrchestrator.start();
        waitForAsyncOperations();

        // then
        then(leadershipHandler).shouldHaveNoInteractions();
        then(client).shouldHaveNoInteractions();
        then(sessionHandler).shouldHaveNoMoreInteractions();
    }

    @Test
    void start_should_reApplyForLeadership_when_RecoverableErrorOccursAndMaxRetriesNotReached() {
        // given
        given(sessionHandler.createNewSession())
                .willReturn(Mono.error(new IllegalStateException("boom")))
                .willReturn(Mono.empty());

        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getMaxRetryAttempts()).willReturn(1);
        given(electionConfiguration.getRetryDelayMs()).willReturn(5);

        // when
        leaderElectionOrchestrator.start();
        waitForAsyncOperations();

        // then
        then(leadershipHandler).shouldHaveNoInteractions();
        then(client).shouldHaveNoInteractions();
        then(sessionHandler).shouldHaveNoMoreInteractions();
    }

    @Test
    void start_should_stopImmediately_when_RecoverableErrorOccursAndMaxRetriesReached() {
        // given
        given(sessionHandler.createNewSession())
                .willReturn(Mono.error(new IllegalStateException("boom")));

        given(sessionHandler.cancelSessionRenewal()).willReturn(Mono.empty());
        given(sessionHandler.destroySession()).willReturn(Mono.empty());
        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getMaxRetryAttempts()).willReturn(0);
        given(electionConfiguration.getTimeoutMs()).willReturn(12);

        // when
        leaderElectionOrchestrator.start();
        waitForAsyncOperations();

        // then
        then(leadershipHandler).shouldHaveNoInteractions();
        then(client).shouldHaveNoInteractions();
        then(sessionHandler).shouldHaveNoMoreInteractions();
    }

    @Test
    void watchForLeadershipInfoChanges_should_keepListenerReference() {
        // given

        // when
        leaderElectionOrchestrator.watchForLeadershipInfoChanges(Mono.empty());

        // then
        then(disposable).should(never()).dispose();
        assertThat(leaderElectionOrchestrator.getListener()).isNotNull();
    }

    @Test
    void watchForLeadershipInfoChanges_should_disposePreviousNoDisposedListener() {
        // given
        leaderElectionOrchestrator.setListener(disposable);
        given(disposable.isDisposed()).willReturn(false);

        // when
        leaderElectionOrchestrator.watchForLeadershipInfoChanges(Mono.empty());

        // then
        then(disposable).should().dispose();
        assertThat(leaderElectionOrchestrator.getListener()).isNotEqualTo(disposable);
    }

    @Test
    void watchForLeadershipInfoChanges_should_notDisposePreviousDisposedListener() {
        // given
        leaderElectionOrchestrator.setListener(disposable);
        given(disposable.isDisposed()).willReturn(true);

        // when
        leaderElectionOrchestrator.watchForLeadershipInfoChanges(Mono.empty());

        // then
        then(disposable).shouldHaveNoMoreInteractions();
        assertThat(leaderElectionOrchestrator.getListener()).isNotEqualTo(disposable);
    }

    @Test
    void watchForLeadershipInfoChanges_should_handleWatchTimeout() {
        // given
        given(configuration.getPath()).willReturn("my-path");
        given(client.watchLeadership("my-path", 1234)).willReturn(Mono.error(TIMEOUT_EXCEPTION));
        leaderElectionOrchestrator.setModifyIndex(5678);
        given(client.watchLeadership("my-path", 5678)).willReturn(Mono.empty());

        // when
        leaderElectionOrchestrator.watchForLeadershipInfoChanges(Mono.just(1234));
        waitForAsyncOperations();

        // then
        then(client).shouldHaveNoMoreInteractions();
    }

    @Test
    void watchForLeadershipInfoChanges_should_immediatelyStop_when_NonRecoverableErrorOccurs() {
        // given
        given(configuration.getPath()).willReturn("my-path");
        given(client.watchLeadership("my-path", 1234)).willReturn(Mono.error(new NonRecoverableElectionException("boom")));

        // when
        leaderElectionOrchestrator.watchForLeadershipInfoChanges(Mono.just(1234));
        waitForAsyncOperations();

        // then
        then(client).shouldHaveNoMoreInteractions();
    }

    @Test
    void watchForLeadershipInfoChanges_stopImmediately_when_RecoverableErrorOccursAndMaxRetriesReached() {
        // given
        given(configuration.getPath()).willReturn("my-path");
        given(client.watchLeadership("my-path", 1234)).willReturn(Mono.error(new RuntimeException("boom")));
        leaderElectionOrchestrator.setModifyIndex(5678);

        given(sessionHandler.cancelSessionRenewal()).willReturn(Mono.empty());
        given(sessionHandler.destroySession()).willReturn(Mono.empty());
        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getMaxRetryAttempts()).willReturn(0);
        given(electionConfiguration.getTimeoutMs()).willReturn(12);

        // when
        leaderElectionOrchestrator.watchForLeadershipInfoChanges(Mono.just(1234));
        waitForAsyncOperations();

        // then
        then(client).shouldHaveNoMoreInteractions();
    }

    @Test
    void watchForLeadershipInfoChanges_should_reWatch_when_RecoverableErrorOccursAndMaxRetriesNotReached() {
        // given
        given(configuration.getPath()).willReturn("my-path");
        given(client.watchLeadership("my-path", 1234)).willReturn(Mono.error(new RuntimeException("boom")));
        leaderElectionOrchestrator.setModifyIndex(5678);
        given(client.watchLeadership("my-path", 5678)).willReturn(Mono.empty());

        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getMaxRetryAttempts()).willReturn(2);
        given(electionConfiguration.getRetryDelayMs()).willReturn(5);

        // when
        leaderElectionOrchestrator.watchForLeadershipInfoChanges(Mono.just(1234));
        waitForAsyncOperations();

        // then
        then(client).shouldHaveNoMoreInteractions();
    }

    @Test
    void onLeadershipChanges_should_doNothing_when_stopping() {
        // given
        leaderElectionOrchestrator.setClosing(true);

        // when
        leaderElectionOrchestrator.onLeadershipChanges(List.of());

        // then
        assertThat(leaderElectionOrchestrator.getModifyIndex()).isNull();
        then(client).shouldHaveNoInteractions();
        then(sessionHandler).shouldHaveNoInteractions();
        then(leadershipHandler).shouldHaveNoInteractions();
        then(leadershipEventsPublisher).shouldHaveNoInteractions();
    }

    @Test
    void onLeadershipChanges_should_applyForLeadership_when_listIsEmpty() {
        // given
        given(sessionHandler.createNewSession()).willReturn(Mono.empty());

        // when
        leaderElectionOrchestrator.onLeadershipChanges(List.of());

        // then
        assertThat(leaderElectionOrchestrator.getModifyIndex()).isNull();
        then(client).shouldHaveNoInteractions();
        then(sessionHandler).shouldHaveNoMoreInteractions();
        then(leadershipHandler).shouldHaveNoInteractions();
        then(leadershipEventsPublisher).shouldHaveNoInteractions();
    }

    @Test
    void onLeadershipChanges_should_applyForLeadership_when_kvHasNoLock() {
        // given
        given(sessionHandler.createNewSession()).willReturn(Mono.empty());
        given(mockedKeyValue.getModifyIndex()).willReturn(1234);
        given(mockedKeyValue.getSession()).willReturn(null);
        given(mockedKeyValue.getValue()).willReturn("my-kv-content");

        // when
        leaderElectionOrchestrator.onLeadershipChanges(List.of(mockedKeyValue));

        // then
        assertThat(leaderElectionOrchestrator.getModifyIndex()).isEqualTo(1234);
        then(client).shouldHaveNoInteractions();
        then(sessionHandler).shouldHaveNoMoreInteractions();
        then(leadershipHandler).shouldHaveNoInteractions();
        then(leadershipEventsPublisher).should().publishLeadershipDetailsChange("my-kv-content");
    }

    @Test
    void onLeadershipChanges_should_keepWatching_when_kvHasLock() {
        // given
        given(mockedKeyValue.getModifyIndex()).willReturn(1234);
        given(mockedKeyValue.getSession()).willReturn("my-session-id");
        given(mockedKeyValue.getValue()).willReturn("my-kv-content");
        given(configuration.getPath()).willReturn("my-path");
        given(client.watchLeadership("my-path", 1234)).willReturn(Mono.empty());

        // when
        leaderElectionOrchestrator.onLeadershipChanges(List.of(mockedKeyValue));

        // then
        assertThat(leaderElectionOrchestrator.getModifyIndex()).isEqualTo(1234);
        assertThat(leaderElectionOrchestrator.getListener()).isNotNull();
        then(client).shouldHaveNoMoreInteractions();
        then(sessionHandler).shouldHaveNoInteractions();
        then(leadershipHandler).shouldHaveNoInteractions();
        then(leadershipEventsPublisher).should().publishLeadershipDetailsChange("my-kv-content");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void stop_should_disposeListener_when_notDisposed(final boolean disposed) {
        // given
        leaderElectionOrchestrator.setListener(disposable);
        leaderElectionOrchestrator.setModifyIndex(1234);
        given(disposable.isDisposed()).willReturn(disposed);
        given(sessionHandler.cancelSessionRenewal()).willReturn(Mono.just("session-id"));
        given(leadershipHandler.releaseLeadership("session-id")).willReturn(Mono.empty());
        given(sessionHandler.destroySession()).willReturn(Mono.empty());
        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getTimeoutMs()).willReturn(100);

        // when
        leaderElectionOrchestrator.stop();
        waitForAsyncOperations();

        // then
        then(disposable).should(times(disposed ? 0 : 1)).dispose();
        assertThat(leaderElectionOrchestrator.getListener()).isNull();
        assertThat(leaderElectionOrchestrator.getModifyIndex()).isNull();
        assertThat(leaderElectionOrchestrator.getClosing()).isTrue();
    }

    @Test
    void stop_should_complete_when_errorOccurs_at_cancelSessionRenewal() {
        // given
        leaderElectionOrchestrator.setListener(disposable);
        leaderElectionOrchestrator.setModifyIndex(1234);
        given(disposable.isDisposed()).willReturn(false);
        given(sessionHandler.cancelSessionRenewal()).willReturn(Mono.error(new RuntimeException("boom")));
        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getTimeoutMs()).willReturn(100);

        // when
        leaderElectionOrchestrator.stop();
        waitForAsyncOperations();

        // then
        then(disposable).should().dispose();
        assertThat(leaderElectionOrchestrator.getListener()).isNull();
        assertThat(leaderElectionOrchestrator.getModifyIndex()).isNull();
        assertThat(leaderElectionOrchestrator.getClosing()).isTrue();
    }

    @Test
    void stop_should_complete_when_errorOccurs_at_releaseLeadership() {
        // given
        leaderElectionOrchestrator.setListener(disposable);
        leaderElectionOrchestrator.setModifyIndex(1234);
        given(disposable.isDisposed()).willReturn(false);
        given(sessionHandler.cancelSessionRenewal()).willReturn(Mono.just("session-id"));
        given(leadershipHandler.releaseLeadership("session-id")).willReturn(Mono.error(new RuntimeException("boom")));
        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getTimeoutMs()).willReturn(100);

        // when
        leaderElectionOrchestrator.stop();
        waitForAsyncOperations();

        // then
        then(disposable).should().dispose();
        assertThat(leaderElectionOrchestrator.getListener()).isNull();
        assertThat(leaderElectionOrchestrator.getModifyIndex()).isNull();
        assertThat(leaderElectionOrchestrator.getClosing()).isTrue();
    }

    @Test
    void stop_should_complete_when_errorOccurs_at_destroySession() {
        // given
        leaderElectionOrchestrator.setListener(disposable);
        leaderElectionOrchestrator.setModifyIndex(1234);
        given(disposable.isDisposed()).willReturn(false);
        given(sessionHandler.cancelSessionRenewal()).willReturn(Mono.just("session-id"));
        given(leadershipHandler.releaseLeadership("session-id")).willReturn(Mono.empty());
        given(sessionHandler.destroySession()).willReturn(Mono.error(new RuntimeException("boom")));
        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getTimeoutMs()).willReturn(100);

        // when
        leaderElectionOrchestrator.stop();
        waitForAsyncOperations();

        // then
        then(disposable).should().dispose();
        assertThat(leaderElectionOrchestrator.getListener()).isNull();
        assertThat(leaderElectionOrchestrator.getModifyIndex()).isNull();
        assertThat(leaderElectionOrchestrator.getClosing()).isTrue();
    }

    private void waitForAsyncOperations() {
        waitForAsyncOperations(200);
    }

    private void waitForAsyncOperations(final int millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
