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
@Requires(property = LeadershipConfiguration.PREFIX + ".auto-start.disabled",
          notEquals = StringUtils.TRUE,
          defaultValue = StringUtils.FALSE)
public class ElectionTrigger {

    /**
     * Handles application startup by initiating the leadership election process.
     * <p>
     * This method is invoked automatically when the application starts up.
     * It retrieves the {@link LeaderElection} bean from the application context
     * and calls its {@code start()} method to begin the election process.
     * </p>
     *
     * @param event the startup event containing the application context
     */
    @EventListener
    void onStart(final StartupEvent event) {
        log.info("Starting Leadership Election");
        event.getSource().getBean(LeaderElection.class).start();
    }

    /**
     * Handles application shutdown by stopping the leadership election process.
     * <p>
     * This method is invoked automatically when the application is shutting down.
     * It retrieves the {@link LeaderElection} bean from the application context
     * and calls its {@code stop()} method to gracefully release any held leadership
     * and clean up resources.
     * </p>
     *
     * @param event the shutdown event containing the application context
     */
    @EventListener
    void onShutdown(final ShutdownEvent event) {
        log.info("Stopping Leadership Election");
        event.getSource().getBean(LeaderElection.class).stop();
    }
}
