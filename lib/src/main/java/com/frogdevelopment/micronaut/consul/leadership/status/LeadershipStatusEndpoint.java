package com.frogdevelopment.micronaut.consul.leadership.status;

import lombok.RequiredArgsConstructor;

import java.util.Map;

import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;

@Endpoint(id = "leadership")
@RequiredArgsConstructor
public class LeadershipStatusEndpoint {

    private final LeadershipStatus leadershipStatus;

    @Read(description = "Return leadership full details")
    public Map<String, Object> leadershipStatus() {
        return Map.of(
                "isLeader", leadershipStatus.isLeader(),
                "details", leadershipStatus.geLeadershipInfo()
        );
    }

}
