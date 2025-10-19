package com.frogdevelopment.micronaut.consul.leadership.event;

import com.frogdevelopment.micronaut.consul.leadership.details.LeadershipDetails;

/**
 * Event published when the current leader's information changes.
 * <p>
 * This event is fired when there is a change in leadership information stored in
 * Consul's key-value store. This can occur when a new leader is elected or when
 * the current leader's metadata changes. All instances (both leaders and followers)
 * receive this event, allowing them to track who the current leader is.
 * </p>
 * <p>
 * Example usage:
 * </p>
 * <pre>{@code
 * @EventListener
 * void onLeadershipInfoChange(LeadershipInfoChangeEvent event) {
 *     LeadershipDetails info = event.leadershipDetails();
 *     // Access leader details like hostname, cluster name, etc.
 * }
 * }</pre>
 *
 * @param leadershipDetails the updated leadership information containing details about the current leader
 * @since 1.0.0
 */
public record LeadershipInfoChangeEvent(LeadershipDetails leadershipDetails) {
}
