package com.frogdevelopment.micronaut.consul.leadership.event;

import lombok.RequiredArgsConstructor;
import lombok.val;

import java.util.Base64;

import com.frogdevelopment.micronaut.consul.leadership.election.LeadershipInfoProvider;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.ApplicationEventPublisher;

@Prototype
@RequiredArgsConstructor
public final class LeadershipEventsPublisher {

    private final LeadershipInfoProvider leadershipInfoProvider;
    private final ApplicationEventPublisher<LeadershipChangeEvent> leadershipChangeEventPublisher;
    private final ApplicationEventPublisher<LeadershipInfoChangeEvent> leadershipInfoChangeEventPublisher;

    private final Base64.Decoder base64Decoder = Base64.getDecoder();

    public void publishLeadershipChangeEvent(final boolean isLeader) {
        leadershipChangeEventPublisher.publishEvent(new LeadershipChangeEvent(isLeader));
    }

    public void publishLeadershipInfoChange(final String encodedValue) {
        val decodedValue = new String(base64Decoder.decode(encodedValue));
        val leadershipInfo = leadershipInfoProvider.convertValue(decodedValue);
        leadershipInfoChangeEventPublisher.publishEvent(new LeadershipInfoChangeEvent(leadershipInfo));
    }
}
