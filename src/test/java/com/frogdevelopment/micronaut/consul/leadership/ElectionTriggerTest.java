package com.frogdevelopment.micronaut.consul.leadership;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.micronaut.consul.leadership.election.LeaderElectionOrchestrator;

import io.micronaut.context.BeanContext;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;

@ExtendWith(MockitoExtension.class)
class ElectionTriggerTest {

    @InjectMocks
    private ElectionTrigger electionTrigger;

    @Mock
    private StartupEvent startupEvent;
    @Mock
    private ShutdownEvent shutdownEvent;
    @Mock
    private BeanContext beanContext;
    @Mock
    private LeaderElectionOrchestrator leaderElectionOrchestrator;

    @Test
    void onStart_should_start() {
        // given
        given(startupEvent.getSource()).willReturn(beanContext);
        given(beanContext.getBean(LeaderElectionOrchestrator.class)).willReturn(leaderElectionOrchestrator);

        // when
        electionTrigger.onStart(startupEvent);

        // then
        then(leaderElectionOrchestrator).should().start();
        then(leaderElectionOrchestrator).shouldHaveNoMoreInteractions();
    }

    @Test
    void onShutdown_should_stop() {
        // given
        given(shutdownEvent.getSource()).willReturn(beanContext);
        given(beanContext.getBean(LeaderElectionOrchestrator.class)).willReturn(leaderElectionOrchestrator);

        // when
        electionTrigger.onShutdown(shutdownEvent);

        // then
        then(leaderElectionOrchestrator).should().stop();
        then(leaderElectionOrchestrator).shouldHaveNoMoreInteractions();
    }
}
