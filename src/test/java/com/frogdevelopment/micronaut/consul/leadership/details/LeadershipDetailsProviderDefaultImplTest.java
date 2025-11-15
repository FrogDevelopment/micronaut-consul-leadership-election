package com.frogdevelopment.micronaut.consul.leadership.details;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.micronaut.consul.leadership.exceptions.NonRecoverableElectionException;
import com.frogdevelopment.micronaut.consul.leadership.kubernetes.KubernetesInfoResolver;

import io.micronaut.context.env.Environment;
import io.micronaut.serde.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class LeadershipDetailsProviderDefaultImplTest {

    @Mock
    private KubernetesInfoResolver kubernetesInfoResolver;
    @Mock
    private Environment environment;
    @Mock
    private ObjectMapper objectMapper;

    @Test
    void should_getLeadershipInfoFromK8S() {
        // given
        final var defaultLeadershipInfoProvider = new LeadershipDetailsProviderDefaultImpl(Optional.of(kubernetesInfoResolver), environment, objectMapper);
        given(kubernetesInfoResolver.resolvePodName()).willReturn(Optional.of("my-podname"));
        given(kubernetesInfoResolver.resolveNamespace()).willReturn(Optional.of("my-namespace"));
        given(kubernetesInfoResolver.resolveClusterName()).willReturn(Optional.of("my-cluster_name"));

        // when
        final var leadershipInfo = defaultLeadershipInfoProvider.getLeadershipInfo(true);

        // then
        assertThat(leadershipInfo).isInstanceOfSatisfying(LeadershipDetailsDefault.class, defaultLeadershipInfo -> {
            assertThat(defaultLeadershipInfo.getPodName()).isEqualTo("my-podname");
            assertThat(defaultLeadershipInfo.getNamespace()).isEqualTo("my-namespace");
            assertThat(defaultLeadershipInfo.getClusterName()).isEqualTo("my-cluster_name");
            assertThat(defaultLeadershipInfo.getAcquireDateTime()).isNotNull();
        });
        then(environment).shouldHaveNoInteractions();
    }

    @Test
    void should_getLeadershipInfoFromFallback() {
        // given
        final var defaultLeadershipInfoProvider = new LeadershipDetailsProviderDefaultImpl(Optional.empty(), environment, objectMapper);
        given(environment.getProperty("hostname", String.class)).willReturn(Optional.empty());
        given(environment.getProperty("micronaut.application.name", String.class)).willReturn(Optional.of("my-podname"));

        // when
        final var leadershipInfo = defaultLeadershipInfoProvider.getLeadershipInfo(true);

        // then
        assertThat(leadershipInfo).isInstanceOfSatisfying(LeadershipDetailsDefault.class, defaultLeadershipInfo -> {
            assertThat(defaultLeadershipInfo.getPodName()).isEqualTo("my-podname");
            assertThat(defaultLeadershipInfo.getNamespace()).isEqualTo("n/a");
            assertThat(defaultLeadershipInfo.getClusterName()).isEqualTo("n/a");
            assertThat(defaultLeadershipInfo.getAcquireDateTime()).isNotNull();
        });
        then(kubernetesInfoResolver).shouldHaveNoInteractions();
    }

    @Test
    void should_convertValue() throws IOException {
        // given
        final var defaultLeadershipInfoProvider = new LeadershipDetailsProviderDefaultImpl(Optional.empty(), environment, objectMapper);

        final var expected = mock(LeadershipDetailsDefault.class);
        final var json = "my_value";
        given(objectMapper.readValue(json, LeadershipDetailsDefault.class)).willReturn(expected);

        // when
        final var actual = defaultLeadershipInfoProvider.convertValue(json);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void should_handleIOException_when_convertValue() throws IOException {
        // given
        final var defaultLeadershipInfoProvider = new LeadershipDetailsProviderDefaultImpl(Optional.empty(), environment, objectMapper);

        final var json = "my_value";
        final var ioException = new IOException("boom");
        given(objectMapper.readValue(json, LeadershipDetailsDefault.class)).willThrow(ioException);

        // when
        final var caught = catchException(() -> defaultLeadershipInfoProvider.convertValue(json));

        // then
        assertThat(caught).isInstanceOf(NonRecoverableElectionException.class)
                .hasCauseExactlyInstanceOf(IOException.class)
                .hasRootCauseMessage("boom")
                .hasMessage("Unable to process leadershipDetails value my_value");
    }

}
