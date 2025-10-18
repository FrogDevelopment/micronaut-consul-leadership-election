package com.frogdevelopment.micronaut.consul.leadership.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.micronaut.consul.leadership.client.DefaultLeadershipInfo;

@ExtendWith(MockitoExtension.class)
class LeadershipStatusEndpointTest {

    @InjectMocks
    private LeadershipStatusEndpoint leadershipStatusEndpoint;

    @Mock
    private LeadershipStatus leadershipStatus;

    @Test
    void should_return_details() {
        // given
        given(leadershipStatus.isLeader()).willReturn(true);
        final var leadershipInfo = DefaultLeadershipInfo.builder()
                .hostname("hostname")
                .clusterName("cluster")
                .acquireDateTime("my-date")
                .build();
        given(leadershipStatus.geLeadershipInfo()).willReturn(leadershipInfo);

        // when
        final var status = leadershipStatusEndpoint.leadershipStatus();

        // then
        assertThat(status)
                .containsEntry("isLeader", true)
                .containsEntry("details", leadershipInfo);
    }

}
