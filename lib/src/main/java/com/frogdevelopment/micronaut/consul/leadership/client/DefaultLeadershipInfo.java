package com.frogdevelopment.micronaut.consul.leadership.client;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import io.micronaut.serde.annotation.Serdeable;

@Value
@Builder
@Serdeable
@Jacksonized
public class DefaultLeadershipInfo implements LeadershipInfo {

    String hostname;
    String clusterName;
    String acquireDateTime;
    String releaseDateTime;

}
