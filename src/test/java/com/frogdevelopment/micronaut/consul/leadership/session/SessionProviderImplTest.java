package com.frogdevelopment.micronaut.consul.leadership.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;
import com.frogdevelopment.micronaut.consul.leadership.kubernetes.KubernetesInfoResolver;

import io.micronaut.context.env.Environment;

@ExtendWith(MockitoExtension.class)
class SessionProviderImplTest {

    @Mock
    private KubernetesInfoResolver kubernetesInfoResolver;
    @Mock
    private Environment environment;
    @Mock
    private LeadershipConfiguration configuration;
    @Mock
    private LeadershipConfiguration.ElectionConfiguration electionConfiguration;

    @Test
    void should_createSessionFromK8s() {
        // given
        final var sessionProvider = new SessionProviderImpl(Optional.of(kubernetesInfoResolver), environment, configuration);

        given(kubernetesInfoResolver.resolvePodName()).willReturn(Optional.of("my-podname"));
        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getSessionLockDelay()).willReturn("3s");
        given(electionConfiguration.getSessionTtl()).willReturn("10s");

        // when
        final var actual = sessionProvider.createSession();

        // then
        assertThat(actual).isNotNull();
        assertThat(actual.name()).isEqualTo("my-podname");
        assertThat(actual.behavior()).isEqualTo(Session.Behavior.RELEASE);
        assertThat(actual.lockDelay()).isEqualTo("3s");
        assertThat(actual.ttl()).isEqualTo("10s");
        then(environment).shouldHaveNoInteractions();
    }

    @Test
    void should_createSessionFromFallback() {
        // given
        final var sessionProvider = new SessionProviderImpl(Optional.empty(), environment, configuration);

        given(environment.getProperty("micronaut.application.name", String.class)).willReturn(Optional.of("my-podname"));
        given(configuration.getElection()).willReturn(electionConfiguration);
        given(electionConfiguration.getSessionLockDelay()).willReturn("3s");
        given(electionConfiguration.getSessionTtl()).willReturn("10s");

        // when
        final var actual = sessionProvider.createSession();

        // then
        assertThat(actual).isNotNull();
        assertThat(actual.name()).isEqualTo("my-podname");
        assertThat(actual.behavior()).isEqualTo(Session.Behavior.RELEASE);
        assertThat(actual.lockDelay()).isEqualTo("3s");
        assertThat(actual.ttl()).isEqualTo("10s");
        then(kubernetesInfoResolver).shouldHaveNoInteractions();
    }

}
