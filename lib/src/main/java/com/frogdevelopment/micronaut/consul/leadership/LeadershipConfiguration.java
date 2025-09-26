package com.frogdevelopment.micronaut.consul.leadership;

import java.time.Duration;
import java.util.Optional;

import jakarta.validation.constraints.NotBlank;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.Toggleable;
import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.serde.annotation.Serdeable;

@Context
@Refreshable
@Serdeable.Serializable
@ConfigurationProperties(LeadershipConfiguration.PREFIX)
public interface LeadershipConfiguration {

    /**
     * The configuration prefix for Consul leadership properties.
     */
    String PREFIX = "consul.leadership";

    Optional<String> getToken();

    ElectionConfiguration getElection();

    // ELECTION
    @NotBlank
    @Bindable(defaultValue = "leadership/${micronaut.application.name}")
    String getPath();

    @ConfigurationProperties("election")
    interface ElectionConfiguration extends Toggleable {

        @Bindable(defaultValue = "5s")
        String getSessionLockDelay();

        @Bindable(defaultValue = "15s")
        String getSessionTtl();

        @Bindable(defaultValue = "10s")
        Duration getSessionRenewalDelay();

//        @Bindable(defaultValue = "3")
//        Integer getMaxRetryAttempts();

        @Bindable(defaultValue = "500")
        Integer getRetryDelayMs();
    }

}
