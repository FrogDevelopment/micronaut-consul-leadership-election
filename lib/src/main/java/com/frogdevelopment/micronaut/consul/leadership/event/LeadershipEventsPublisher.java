package com.frogdevelopment.micronaut.consul.leadership.event;

import lombok.RequiredArgsConstructor;
import lombok.val;

import java.util.Base64;

import com.frogdevelopment.micronaut.consul.leadership.details.LeadershipDetailsProvider;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.ApplicationEventPublisher;

/**
 * Publisher for leadership-related application events.
 * <p>
 * This class is responsible for publishing events when leadership changes occur
 * or when leadership information is updated. It decodes leadership information
 * from Consul's base64-encoded values and publishes appropriate event types
 * that can be consumed by application event listeners.
 * </p>
 * <p>
 * Two types of events are published:
 * </p>
 * <ul>
 *   <li>{@link LeadershipChangeEvent} - when this instance acquires or loses leadership</li>
 *   <li>{@link LeadershipInfoChangeEvent} - when the current leader's information changes</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Prototype
@RequiredArgsConstructor
public final class LeadershipEventsPublisher {

    private final LeadershipDetailsProvider leadershipDetailsProvider;
    private final ApplicationEventPublisher<LeadershipChangeEvent> leadershipChangeEventPublisher;
    private final ApplicationEventPublisher<LeadershipInfoChangeEvent> leadershipInfoChangeEventPublisher;

    private final Base64.Decoder base64Decoder = Base64.getDecoder();

    /**
     * Publishes a leadership change event.
     * <p>
     * This method is called when this instance's leadership status changes,
     * either when acquiring leadership (becoming the leader) or losing leadership.
     * Listeners can subscribe to {@link LeadershipChangeEvent} to react to these changes.
     * </p>
     *
     * @param isLeader {@code true} if this instance has become the leader,
     *                 {@code false} if this instance has lost leadership
     */
    public void publishLeadershipChangeEvent(final boolean isLeader) {
        leadershipChangeEventPublisher.publishEvent(new LeadershipChangeEvent(isLeader));
    }

    /**
     * Publishes a leadership information change event.
     * <p>
     * This method decodes the base64-encoded leadership information from Consul's
     * key-value store and publishes an event containing the decoded leadership details.
     * This allows listeners to track changes in leader information, such as when
     * a different instance becomes the leader or when the leader's metadata changes.
     * </p>
     *
     * @param encodedValue the base64-encoded leadership information from Consul
     */
    public void publishLeadershipInfoChange(final String encodedValue) {
        val decodedValue = new String(base64Decoder.decode(encodedValue));
        val leadershipInfo = leadershipDetailsProvider.convertValue(decodedValue);
        leadershipInfoChangeEventPublisher.publishEvent(new LeadershipInfoChangeEvent(leadershipInfo));
    }
}
