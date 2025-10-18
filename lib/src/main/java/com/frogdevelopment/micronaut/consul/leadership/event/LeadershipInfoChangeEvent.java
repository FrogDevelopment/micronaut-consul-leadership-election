package com.frogdevelopment.micronaut.consul.leadership.event;

import com.frogdevelopment.micronaut.consul.leadership.client.LeadershipInfo;

public record LeadershipInfoChangeEvent(LeadershipInfo leadershipInfo) {
}
