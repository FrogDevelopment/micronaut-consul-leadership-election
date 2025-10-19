package com.frogdevelopment.micronaut.consul.leadership.election;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;
import com.frogdevelopment.micronaut.consul.leadership.client.ConsulLeadershipClient;
import com.frogdevelopment.micronaut.consul.leadership.details.LeadershipDetails;
import com.frogdevelopment.micronaut.consul.leadership.details.LeadershipDetailsProvider;
import com.frogdevelopment.micronaut.consul.leadership.event.LeadershipEventsPublisher;
import com.frogdevelopment.micronaut.consul.leadership.exceptions.NonRecoverableElectionException;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class LeadershipHandlerTest {

    @InjectMocks
    private LeadershipHandlerImpl leadershipHandler;

    @Mock
    private ConsulLeadershipClient client;
    @Mock
    private LeadershipConfiguration configuration;
    @Mock
    private LeadershipDetailsProvider leadershipDetailsProvider;
    @Mock
    private LeadershipEventsPublisher leadershipEventsPublisher;

    @Mock
    private LeadershipDetails leadershipDetails;

    @Test
    void acquireLeadership_should_stop_when_getLeadershipInfoFails() {
        // given
        given(leadershipDetailsProvider.getLeadershipInfo(true)).willThrow(new RuntimeException("boom"));

        // when
        final var caught = catchException(() -> leadershipHandler.acquireLeadership("sessionId").block());

        // then
        assertThat(caught).isInstanceOf(NonRecoverableElectionException.class)
                .hasMessage("LeadershipDetails creation failed")
                .hasRootCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("boom");
        then(client).shouldHaveNoInteractions();
        then(leadershipEventsPublisher).shouldHaveNoInteractions();
    }

    @Test
    void acquireLeadership_should_return_FALSE_when_acquireLeadershipFails() {
        // given
        given(leadershipDetailsProvider.getLeadershipInfo(true)).willReturn(leadershipDetails);
        given(configuration.getPath()).willReturn("path");
        given(client.acquireLeadership("path", leadershipDetails, "sessionId")).willReturn(Mono.error(new RuntimeException("boom boom")));

        // when
        final var result = leadershipHandler.acquireLeadership("sessionId").block();

        // then
        then(client).shouldHaveNoMoreInteractions();
        then(leadershipEventsPublisher).should().publishLeadershipChangeEvent(false);
        assertThat(result).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void acquireLeadership_should_return_resultFromClient(final boolean acquireLeadership) {
        // given
        given(leadershipDetailsProvider.getLeadershipInfo(true)).willReturn(leadershipDetails);
        given(configuration.getPath()).willReturn("path");
        given(client.acquireLeadership("path", leadershipDetails, "sessionId")).willReturn(Mono.just(acquireLeadership));

        // when
        final var result = leadershipHandler.acquireLeadership("sessionId").block();

        // then
        then(client).shouldHaveNoMoreInteractions();
        then(leadershipEventsPublisher).should().publishLeadershipChangeEvent(acquireLeadership);
        assertThat(result).isEqualTo(acquireLeadership);
    }


    @Test
    void readLeadershipInfo_should_stop_when_readLeadershipFails() {
        // given
        given(configuration.getPath()).willReturn("path");
        given(client.readLeadership("path")).willThrow(new RuntimeException("boom"));

        // when
        final var caught = catchException(() -> leadershipHandler.readLeadershipInfo().block());

        // then
        assertThat(caught).isInstanceOf(NonRecoverableElectionException.class)
                .hasMessage("Failed to retrieve leadership information")
                .hasRootCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("boom");
        then(client).shouldHaveNoMoreInteractions();
        then(leadershipEventsPublisher).shouldHaveNoInteractions();
    }

}
