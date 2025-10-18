package com.frogdevelopment.micronaut.consul.leadership.status;

import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Singleton;

import com.frogdevelopment.micronaut.consul.leadership.client.LeadershipInfo;
import com.frogdevelopment.micronaut.consul.leadership.event.LeadershipChangeEvent;
import com.frogdevelopment.micronaut.consul.leadership.event.LeadershipInfoChangeEvent;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.runtime.event.annotation.EventListener;

@Slf4j
@Singleton
public class LeadershipStatusImpl implements LeadershipStatus {

    private boolean isLeader;
    private LeadershipInfo leadershipInfo;

    @Override
    public boolean isLeader() {
        return isLeader;
    }

    @Override
    public LeadershipInfo geLeadershipInfo() {
        return leadershipInfo;
    }

    @EventListener
    public void onLeadershipChanged(@NonNull final LeadershipChangeEvent event) {
        this.isLeader = event.isLeader();
        log.debug("Current leader: {}", isLeader);
    }

    @EventListener
    public void onLeadershipInfoChanged(@NonNull final LeadershipInfoChangeEvent event) {
        this.leadershipInfo = event.leadershipInfo();
        log.debug("Current leader information: {}", leadershipInfo);
    }

}
