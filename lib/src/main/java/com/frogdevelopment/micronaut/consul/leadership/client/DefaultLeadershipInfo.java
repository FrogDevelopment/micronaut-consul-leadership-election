package com.frogdevelopment.micronaut.consul.leadership.client;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Value
@Builder
@Serdeable
@Jacksonized
public class DefaultLeadershipInfo implements LeadershipInfo {

    @Nullable
    String hostname;
    @Nullable
    String clusterName;
    @Nullable
    String acquireDateTime;
    @Nullable
    String releaseDateTime;

}
