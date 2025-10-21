package com.frogdevelopment.micronaut.consul.leadership.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;

import io.micronaut.context.env.Environment;

@ExtendWith(MockitoExtension.class)
class SessionProviderDefaultImplTest {

    @InjectMocks
    private SessionProviderDefaultImpl sessionProvider;

    @Mock
    private Environment environment;
    @Mock
    private LeadershipConfiguration configuration;
    @Mock
    private LeadershipConfiguration.ElectionConfiguration electionConfiguration;

    @Test
    void should_createSession() {
        // given
        given(environment.get("hostname", String.class, "n/a")).willReturn("my-hostname");
        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getSessionLockDelay()).willReturn("3s");
        given(electionConfiguration.getSessionTtl()).willReturn("10s");

        // when
        final var actual = sessionProvider.createSession();

        // then
        assertThat(actual).isNotNull();
        assertThat(actual.name()).isEqualTo("my-hostname");
        assertThat(actual.behavior()).isEqualTo(Session.Behavior.RELEASE);
        assertThat(actual.lockDelay()).isEqualTo("3s");
        assertThat(actual.ttl()).isEqualTo("10s");

    }

}
