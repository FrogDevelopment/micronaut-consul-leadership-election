package com.frogdevelopment.micronaut.consul.leadership.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.micronaut.consul.leadership.details.LeadershipDetails;
import com.frogdevelopment.micronaut.consul.leadership.details.LeadershipDetailsProvider;

import io.micronaut.context.event.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class LeadershipEventsPublisherTest {

    private LeadershipEventsPublisher leadershipEventsPublisher;

    @Mock
    private LeadershipDetailsProvider leadershipDetailsProvider;
    @Mock
    private ApplicationEventPublisher<LeadershipChangeEvent> leadershipChangeEventPublisher;
    @Mock
    private ApplicationEventPublisher<LeadershipDetailsChangeEvent> leadershipDetailsChangeEventPublisher;

    @Captor
    private ArgumentCaptor<LeadershipChangeEvent> leadershipChangeEventCaptor;

    @Mock
    private LeadershipDetails leadershipDetails;
    @Captor
    private ArgumentCaptor<LeadershipDetailsChangeEvent> leadershipDetailsChangeEventCaptor;

    @BeforeEach()
    void beforeEach() {
        leadershipEventsPublisher = new LeadershipEventsPublisher(leadershipDetailsProvider, leadershipChangeEventPublisher, leadershipDetailsChangeEventPublisher);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_publishLeadershipChangeEvent(final boolean leader) {
        // given

        // when
        leadershipEventsPublisher.publishLeadershipChangeEvent(leader);

        // then
        then(leadershipChangeEventPublisher).should().publishEvent(leadershipChangeEventCaptor.capture());
        final var changeEvent = leadershipChangeEventCaptor.getValue();
        assertThat(changeEvent.isLeader()).isEqualTo(leader);
    }

    @Test
    void should_publishLeadershipDetailsChange() {
        // given
        final var value = "test";
        final var encodedValue = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        given(leadershipDetailsProvider.convertValue(value)).willReturn(leadershipDetails);

        // when
        leadershipEventsPublisher.publishLeadershipDetailsChange(encodedValue);

        // then
        then(leadershipDetailsChangeEventPublisher).should().publishEvent(leadershipDetailsChangeEventCaptor.capture());
        final var changeEvent = leadershipDetailsChangeEventCaptor.getValue();
        assertThat(changeEvent.leadershipDetails()).isEqualTo(leadershipDetails);
    }

}
