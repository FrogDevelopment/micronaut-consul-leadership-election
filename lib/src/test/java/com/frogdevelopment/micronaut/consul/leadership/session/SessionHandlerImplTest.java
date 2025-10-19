package com.frogdevelopment.micronaut.consul.leadership.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;
import com.frogdevelopment.micronaut.consul.leadership.client.ConsulLeadershipClient;
import com.frogdevelopment.micronaut.consul.leadership.election.NonRecoverableElectionException;

import io.micronaut.scheduling.TaskScheduler;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class SessionHandlerImplTest {

    @InjectMocks
    private SessionHandlerImpl sessionHandler;

    @Mock
    private ConsulLeadershipClient client;
    @Mock
    private LeadershipConfiguration configuration;
    @Mock
    private SessionProvider sessionProvider;
    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private Session session;
    @Mock
    private LeadershipConfiguration.ElectionConfiguration electionConfiguration;
    @Mock
    private ScheduledFuture<?> scheduledFuture;

    @Test
    void createNewSession_should_returnNewlyCreatedSessionId() {
        // given
        given(sessionProvider.createSession()).willReturn(session);
        given(client.createSession(session)).willReturn(Mono.just(session));
        given(session.id()).willReturn("my-session-id");

        // when
        final var result = sessionHandler.createNewSession().block();

        // then
        assertThat(result).isEqualTo("my-session-id");
        then(client).shouldHaveNoMoreInteractions();
    }

    @Test
    void createNewSession_should_throwNonRecoverableElectionException_when_sessionProviderFails() {
        // given
        given(sessionProvider.createSession()).willThrow(new RuntimeException("boom"));

        // when
        final var caught = catchException(() -> sessionHandler.createNewSession().block());

        // then
        assertThat(caught).isInstanceOf(NonRecoverableElectionException.class)
                .hasMessage("Session creation failed")
                .hasCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("boom");
        then(client).shouldHaveNoInteractions();
    }

    @Test
    void createNewSession_should_throwNonRecoverableElectionException_when_client() {
        // given
        given(sessionProvider.createSession()).willReturn(session);
        given(client.createSession(session)).willReturn(Mono.error((new RuntimeException("boom boom"))));

        // when
        final var caught = catchException(() -> sessionHandler.createNewSession().block());

        // then
        assertThat(caught).isInstanceOf(NonRecoverableElectionException.class)
                .hasMessage("Session creation failed")
                .hasCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("boom boom");
        then(client).shouldHaveNoMoreInteractions();
    }

    @Test
    void destroySession_should_call() {
        // given
        given(client.destroySession("my-session-id")).willReturn(Mono.empty());

        // when
        sessionHandler.destroySession("my-session-id").block();

        // then
        then(client).shouldHaveNoMoreInteractions();
    }

    @Test
    void destroySession_should_continueDespiteError() {
        // given
        given(client.destroySession("my-session-id")).willReturn(Mono.error(new RuntimeException("boom")));

        // when
        sessionHandler.destroySession("my-session-id").block();

        // then
        then(client).shouldHaveNoMoreInteractions();
    }

    @Test
    void scheduleSessionRenewal_should_scheduleSessionRenewal() {
        // given
        given(configuration.getElection()).willReturn(electionConfiguration);
        final var sessionRenewalDelay = Duration.ofMillis(500);
        given(electionConfiguration.getSessionRenewalDelay()).willReturn(sessionRenewalDelay);
        given(taskScheduler.scheduleWithFixedDelay(eq(Duration.ZERO), eq(sessionRenewalDelay), any(Runnable.class)))
                .willAnswer(invocation -> scheduledFuture);

        // when
        sessionHandler.scheduleSessionRenewal("my-session-id").block();

        // then
        final var actual = sessionHandler.getScheduledFuture();
        assertThat(actual).isEqualTo(scheduledFuture);
    }

    @Test
    void renewSession_should_handleError() {
        // given
        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getTimeoutMs()).willReturn(500);
        given(client.renewSession("my-session-id")).willReturn(Mono.error(new RuntimeException("boom")));

        // when
        sessionHandler.renewSession("my-session-id");

        // then
        then(client).shouldHaveNoMoreInteractions();
        // todo check logs
    }

    @Test
    void renewSession_should_renewSession() {
        // given
        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getTimeoutMs()).willReturn(500);
        given(client.renewSession("my-session-id")).willReturn(Mono.empty());

        // when
        sessionHandler.renewSession("my-session-id");

        // then
        then(client).shouldHaveNoMoreInteractions();
    }

    @Test
    void cancelSessionRenewal_should_cancelScheduledFuture() {
        // given
        sessionHandler.setScheduledFuture(scheduledFuture);
        given(scheduledFuture.cancel(true)).willReturn(true);

        // when
        sessionHandler.cancelSessionRenewal().block();

        //
        then(scheduledFuture).shouldHaveNoMoreInteractions();
    }

    @Test
    void cancelSessionRenewal_should_logWarning_when_cancellationFails() {
        // given
        sessionHandler.setScheduledFuture(scheduledFuture);
        given(scheduledFuture.cancel(true)).willReturn(false);

        // when
        sessionHandler.cancelSessionRenewal().block();

        //
        then(scheduledFuture).shouldHaveNoMoreInteractions();
        // todo check logs
    }
}
