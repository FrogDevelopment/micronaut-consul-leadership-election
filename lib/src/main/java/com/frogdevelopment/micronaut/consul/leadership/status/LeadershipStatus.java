package com.frogdevelopment.micronaut.consul.leadership.status;

import com.frogdevelopment.micronaut.consul.leadership.client.LeadershipInfo;

import io.micronaut.core.annotation.Nullable;

public interface LeadershipStatus {

    /**
     * Checks if the current instance is the leader.
     *
     * @return {@code true} if this instance is currently the leader, {@code false} otherwise
     */
    boolean isLeader();

    @Nullable
    LeadershipInfo geLeadershipInfo();
}
