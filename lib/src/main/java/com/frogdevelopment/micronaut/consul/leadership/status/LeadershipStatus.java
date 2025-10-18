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

    /**
     * Gets the current leadership information.
     * <p>
     * This method returns information about the current leader, which may be this
     * instance or another instance in the cluster. The information typically includes
     * details such as hostname, cluster name, and leadership acquisition timestamp.
     * </p>
     *
     * @return the current leadership information, or {@code null} if no leader information is available yet
     */
    @Nullable
    LeadershipInfo geLeadershipInfo();
}
