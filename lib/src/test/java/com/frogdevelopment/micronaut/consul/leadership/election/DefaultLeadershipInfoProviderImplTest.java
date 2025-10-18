package com.frogdevelopment.micronaut.consul.leadership.election;

import static com.frogdevelopment.micronaut.consul.leadership.election.DefaultLeadershipInfoProviderImpl.DEFAULT_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.micronaut.consul.leadership.client.DefaultLeadershipInfo;

import io.micronaut.context.env.Environment;
import io.micronaut.serde.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DefaultLeadershipInfoProviderImplTest {

    @InjectMocks
    private DefaultLeadershipInfoProviderImpl defaultLeadershipInfoProvider;

    @Mock
    private Environment environment;
    @Mock
    private ObjectMapper objectMapper;

    @Test
    void should_getLeadershipInfo_when_acquiring() {
        // given
        given(environment.get("hostname", String.class, DEFAULT_VALUE)).willReturn("my-hostname");
        given(environment.get("cluster_name", String.class, DEFAULT_VALUE)).willReturn("my-cluster_name");

        // when
        final var leadershipInfo = defaultLeadershipInfoProvider.getLeadershipInfo(true);

        // then
        assertThat(leadershipInfo).isInstanceOfSatisfying(DefaultLeadershipInfo.class, defaultLeadershipInfo -> {
            assertThat(defaultLeadershipInfo.getHostname()).isEqualTo("my-hostname");
            assertThat(defaultLeadershipInfo.getClusterName()).isEqualTo("my-cluster_name");
            assertThat(defaultLeadershipInfo.getAcquireDateTime()).isNotNull();
            assertThat(defaultLeadershipInfo.getReleaseDateTime()).isNull();
        });
    }

    @Test
    void should_getLeadershipInfo_when_releasing() {
        // given
        given(environment.get("hostname", String.class, DEFAULT_VALUE)).willReturn("my-hostname");
        given(environment.get("cluster_name", String.class, DEFAULT_VALUE)).willReturn("my-cluster_name");

        // when
        final var leadershipInfo = defaultLeadershipInfoProvider.getLeadershipInfo(false);

        // then
        assertThat(leadershipInfo).isInstanceOfSatisfying(DefaultLeadershipInfo.class, defaultLeadershipInfo -> {
            assertThat(defaultLeadershipInfo.getHostname()).isEqualTo("my-hostname");
            assertThat(defaultLeadershipInfo.getClusterName()).isEqualTo("my-cluster_name");
            assertThat(defaultLeadershipInfo.getAcquireDateTime()).isNull();
            assertThat(defaultLeadershipInfo.getReleaseDateTime()).isNotNull();
        });
    }

    @Test
    void should_convertValue() throws IOException {
        // given
        final var expected = mock(DefaultLeadershipInfo.class);
        final var json = "my_value";
        given(objectMapper.readValue(json, DefaultLeadershipInfo.class)).willReturn(expected);

        // when
        final var actual = defaultLeadershipInfoProvider.convertValue(json);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void should_handleIOException_when_convertValue() throws IOException {
        // given
        final var json = "my_value";
        final var ioException = new IOException("boom");
        given(objectMapper.readValue(json, DefaultLeadershipInfo.class)).willThrow(ioException);

        // when
        final var caught = catchException(() -> defaultLeadershipInfoProvider.convertValue(json));

        // then
        assertThat(caught).isInstanceOf(NonRecoverableElectionException.class)
                .hasCauseExactlyInstanceOf(IOException.class)
                .hasRootCauseMessage("boom")
                .hasMessage("Unable to process leadershipInfo value my_value");
    }

}
