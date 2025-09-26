package com.frogdevelopment.micronaut.consul.leadership.election;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

import jakarta.inject.Singleton;

import com.frogdevelopment.micronaut.consul.leadership.client.DefaultLeadershipInfo;
import com.frogdevelopment.micronaut.consul.leadership.client.LeadershipInfo;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;

@Singleton
@RequiredArgsConstructor
@Requires(missingBeans = LeadershipInfoProvider.class)
final class DefaultLeadershipInfoProviderImpl implements LeadershipInfoProvider {

    private final Environment environment;

    @Override
    public LeadershipInfo getLeadershipInfo(final boolean isAcquire) {
        final var builder = DefaultLeadershipInfo.builder()
                .hostname(environment.get("hostname", String.class, ""))
                .clusterName(environment.get("cluster_name", String.class, ""));
        if (isAcquire) {
            builder.acquireDateTime(LocalDateTime.now().toString());
        } else {
            builder.releaseDateTime(LocalDateTime.now().toString());
        }
        return builder.build();
    }
}
