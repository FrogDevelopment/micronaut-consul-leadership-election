package com.frogdevelopment.micronaut.consul.leadership.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.micronaut.consul.leadership.details.LeadershipDetailsDefault;
import com.frogdevelopment.micronaut.consul.leadership.event.LeadershipChangeEvent;
import com.frogdevelopment.micronaut.consul.leadership.event.LeadershipDetailsChangeEvent;
import com.frogdevelopment.micronaut.consul.leadership.kubernetes.UpdatePodLabel;

@ExtendWith(MockitoExtension.class)
class LeadershipStatusImplTest {

    @Mock
    private UpdatePodLabel updatePodLabel;

    @ParameterizedTest
    @CsvSource({
            "true,true",
            "false,false",
    })
    void should_listenForLeaderChange(final boolean isLeader, final boolean expected) {
        // given
        final var leadershipStatus = new LeadershipStatusImpl(Optional.empty());

        assertThat(leadershipStatus.isLeader()).isFalse();

        // when
        leadershipStatus.onLeadershipChanged(new LeadershipChangeEvent(isLeader));

        // then
        assertThat(leadershipStatus.isLeader()).isEqualTo(expected);
        then(updatePodLabel).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @CsvSource({
            "true,true",
            "false,false",
    })
    void should_listenForLeaderChangeAndUpdatePodLabel_when_present(final boolean isLeader, final boolean expected) {
        // given
        final var leadershipStatus = new LeadershipStatusImpl(Optional.of(updatePodLabel));

        assertThat(leadershipStatus.isLeader()).isFalse();

        // when
        leadershipStatus.onLeadershipChanged(new LeadershipChangeEvent(isLeader));

        // then
        assertThat(leadershipStatus.isLeader()).isEqualTo(expected);
        then(updatePodLabel).should().updatePodLabel(expected);
    }

    @Test
    void should_listenForLeaderInfoChange() {
        // given
        final var leadershipStatus = new LeadershipStatusImpl(Optional.empty());
        assertThat(leadershipStatus.geLeadershipInfo()).isNull();
        final var leadershipInfo = LeadershipDetailsDefault.builder().build();

        // when
        leadershipStatus.onLeadershipInfoChanged(new LeadershipDetailsChangeEvent(leadershipInfo));

        // then
        assertThat(leadershipStatus.geLeadershipInfo()).isEqualTo(leadershipInfo);
    }
}
