package com.frogdevelopment.micronaut.consul.leadership.event;

public record LeadershipChangeEvent(boolean isLeader) {
}
