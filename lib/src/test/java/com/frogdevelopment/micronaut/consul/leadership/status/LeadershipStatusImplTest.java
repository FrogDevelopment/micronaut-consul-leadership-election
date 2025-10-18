package com.frogdevelopment.micronaut.consul.leadership.status;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.micronaut.consul.leadership.client.DefaultLeadershipInfo;
import com.frogdevelopment.micronaut.consul.leadership.event.LeadershipChangeEvent;
import com.frogdevelopment.micronaut.consul.leadership.event.LeadershipInfoChangeEvent;

@ExtendWith(MockitoExtension.class)
class LeadershipStatusImplTest {

    @InjectMocks
    private LeadershipStatusImpl leadershipStatus;

    @Test
    void should_listenForLeaderChange() {
        // given
        assertThat(leadershipStatus.isLeader()).isFalse();

        // when
        leadershipStatus.onLeadershipChanged(new LeadershipChangeEvent(true));

        // then
        assertThat(leadershipStatus.isLeader()).isTrue();
    }

    @Test
    void should_listenForLeaderInfoChange() {
        // given
        assertThat(leadershipStatus.geLeadershipInfo()).isNull();
        final var leadershipInfo = DefaultLeadershipInfo.builder().build();

        // when
        leadershipStatus.onLeadershipInfoChanged(new LeadershipInfoChangeEvent(leadershipInfo));

        // then
        assertThat(leadershipStatus.geLeadershipInfo()).isEqualTo(leadershipInfo);
    }
}
