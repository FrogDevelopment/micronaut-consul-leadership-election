package com.frogdevelopment.micronaut.consul.leadership.kubernetes;

import jakarta.validation.constraints.NotBlank;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.Toggleable;
import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.serde.annotation.Serdeable;

@Context
@Refreshable
@Serdeable.Serializable
@ConfigurationProperties(LeadershipPodLabelConfiguration.PREFIX)
public interface LeadershipPodLabelConfiguration extends Toggleable {

    String PREFIX = LeadershipConfiguration.PREFIX + ".pod-label";

    @NotBlank
    @Bindable(defaultValue = "leadership-status")
    String getKey();

    @NotBlank
    @Bindable(defaultValue = "leader")
    String getLabelForLeader();

    @NotBlank
    @Bindable(defaultValue = "follower")
    String getLabelForFollower();
}
