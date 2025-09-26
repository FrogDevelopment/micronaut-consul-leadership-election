package com.frogdevelopment.micronaut.consul.leadership.election;

import com.frogdevelopment.micronaut.consul.leadership.client.LeadershipInfo;

import io.micronaut.context.annotation.DefaultImplementation;

@DefaultImplementation(DefaultLeadershipInfoProviderImpl.class)
public interface LeadershipInfoProvider {

    LeadershipInfo getLeadershipInfo(boolean isAcquire);
}
