package com.frogdevelopment.micronaut.consul.leadership.event;

/**
 * Event published when this instance's leadership status changes.
 * <p>
 * This event is fired when the application instance either acquires or loses leadership
 * in the distributed election process. Applications can listen for this event to react
 * to leadership changes, such as starting or stopping leader-specific tasks.
 * </p>
 * <p>
 * Example usage:
 * </p>
 * <pre>{@code
 * @EventListener
 * void onLeadershipChange(LeadershipChangeEvent event) {
 *     if (event.isLeader()) {
 *         // This instance became the leader
 *     } else {
 *         // This instance lost leadership
 *     }
 * }
 * }</pre>
 *
 * @param isLeader {@code true} if this instance has become the leader,
 *                 {@code false} if this instance has lost leadership
 * @since 1.0.0
 */
public record LeadershipChangeEvent(boolean isLeader) {
}
