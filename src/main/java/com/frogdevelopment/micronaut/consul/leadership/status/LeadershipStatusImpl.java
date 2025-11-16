package com.frogdevelopment.micronaut.consul.leadership.status;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Singleton;

import com.frogdevelopment.micronaut.consul.leadership.details.LeadershipDetails;
import com.frogdevelopment.micronaut.consul.leadership.event.LeadershipChangeEvent;
import com.frogdevelopment.micronaut.consul.leadership.event.LeadershipDetailsChangeEvent;
import com.frogdevelopment.micronaut.consul.leadership.kubernetes.UpdatePodLabel;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.runtime.event.annotation.EventListener;

/**
 * Implementation of {@link LeadershipStatus} that tracks leadership state via event listeners.
 * <p>
 * This implementation maintains the current leadership status by listening to
 * {@link LeadershipChangeEvent} and {@link LeadershipDetailsChangeEvent} published
 * during the leadership election process. It provides a simple way to query the
 * current leadership state without directly interacting with Consul.
 * </p>
 * <p>
 * The status is kept in-memory and updated automatically whenever leadership
 * changes occur in the system. This makes it efficient for frequent status checks,
 * such as from management endpoints or application logic.
 * </p>
 *
 * @since 1.0.0
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class LeadershipStatusImpl implements LeadershipStatus {

    private final Optional<UpdatePodLabel> updatePodLabel;

    private final AtomicBoolean isLeader = new AtomicBoolean(false);
    private final AtomicReference<LeadershipDetails> leadershipDetails = new AtomicReference<>();

    @Override
    public boolean isLeader() {
        return isLeader.get();
    }

    @Override
    public LeadershipDetails getLeadershipInfo() {
        return leadershipDetails.get();
    }

    /**
     * Event listener that updates the leadership status when leadership changes.
     * <p>
     * This method is automatically invoked when a {@link LeadershipChangeEvent}
     * is published, updating the internal state to reflect whether this instance
     * is currently the leader or not.
     * </p>
     *
     * @param event the leadership change event containing the new leadership status
     */
    @EventListener
    public void onLeadershipChanged(@NonNull final LeadershipChangeEvent event) {
        final var leader = event.isLeader();
        this.isLeader.set(leader);
        log.debug("Current leader: {}", leader);

        updatePodLabel.ifPresent(podLabel -> podLabel.updatePodLabel(leader));
    }

    /**
     * Event listener that updates the cached leadership information.
     * <p>
     * This method is automatically invoked when a {@link LeadershipDetailsChangeEvent}
     * is published, updating the internal cache with the latest information about
     * the current leader. This information can then be queried via {@link #getLeadershipInfo()}.
     * </p>
     *
     * @param event the leadership info change event containing updated leader information
     */
    @EventListener
    public void onLeadershipInfoChanged(@NonNull final LeadershipDetailsChangeEvent event) {
        final var details = event.leadershipDetails();
        this.leadershipDetails.set(details);
        log.debug("Current leader information: {}", details);
    }

}