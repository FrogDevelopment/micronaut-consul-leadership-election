package com.frogdevelopment.micronaut.consul.leadership.election;

import lombok.extern.slf4j.Slf4j;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.event.annotation.EventListener;

/**
 * Start and stop Consul {@link LeaderElection} on {@link StartupEvent} & {@link ShutdownEvent}.
 *
 * @since 1.0.0
 */
@Slf4j
@Prototype
@Requires(property = LeadershipConfiguration.PREFIX + ".auto-start.disabled", notEquals = StringUtils.TRUE,
          defaultValue = StringUtils.FALSE)
public class ElectionTrigger {

    @EventListener
    void onStart(final StartupEvent event) {
        log.info("Starting Leadership Election");
        event.getSource().getBean(LeaderElection.class).start();
    }

    @EventListener
    void onShutdown(final ShutdownEvent event) {
        log.info("Stopping Leadership Election");
        event.getSource().getBean(LeaderElection.class).stop();
    }
}
