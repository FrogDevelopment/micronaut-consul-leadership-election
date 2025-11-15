package com.frogdevelopment.micronaut.consul.leadership;

import java.time.Duration;
import java.util.Optional;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.Toggleable;
import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Configuration properties for Consul leadership election.
 * <p>
 * This interface defines the configuration options available for setting up
 * and managing leadership election using Consul. It includes authentication
 * settings, election path configuration, and detailed election behavior settings.
 * </p>
 *
 * @since 1.0.0
 */
@Context
@Refreshable
@Serdeable.Serializable
@ConfigurationProperties(LeadershipConfiguration.PREFIX)
public interface LeadershipConfiguration {

    /**
     * The configuration prefix for Consul leadership properties.
     */
    String PREFIX = "consul.leadership";

    /**
     * Gets the Consul authentication token for ACL authentication.
     * <p>
     * This token is used to authenticate with the Consul server when performing
     * leadership election operations. The token is <strong>optional</strong> and only
     * required when Consul's Access Control List (ACL) system is enabled.
     * </p>
     * <p>
     * <strong>When to provide a token:</strong>
     * </p>
     * <ul>
     *   <li><strong>ACLs enabled:</strong> A valid token with appropriate permissions
     *       (session:write, kv:write, kv:read) is required</li>
     *   <li><strong>ACLs disabled:</strong> Token is not needed; requests will be
     *       sent without authentication</li>
     * </ul>
     * <p>
     * <strong>Security best practice:</strong> Store tokens in environment variables
     * or secure configuration management systems rather than plain text configuration files.
     * </p>
     *
     * @return the Consul authentication token, or {@code Optional.empty()} if not configured
     */
    Optional<String> getToken();

    /**
     * Gets the election-specific configuration settings.
     *
     * @return the election configuration containing timing and behavior settings
     */
    ElectionConfiguration getElection();

    /**
     * Gets the Consul key-value path where leadership information is stored.
     * <p>
     * This path is used as the key in Consul's KV store to coordinate leadership
     * election. Multiple instances competing for leadership will use this same path.
     * The default value incorporates the application name to avoid conflicts.
     * </p>
     *
     * @return the Consul KV path for leadership coordination
     */
    @NotBlank
    @Bindable(defaultValue = "leadership/${micronaut.application.name}")
    String getPath();

    /**
     * Configuration properties specific to the leadership election process.
     * <p>
     * This interface defines timing and behavioral settings that control how
     * the leadership election process operates, including session management,
     * retry logic, and timeouts.
     * </p>
     */
    @ConfigurationProperties("election")
    interface ElectionConfiguration extends Toggleable {

        /**
         * Gets the lock delay for Consul sessions.
         * <p>
         * This is the time that must pass before a session can acquire a lock
         * after the previous session holding the lock is destroyed. This prevents
         * rapid lock acquisition and provides stability during leadership transitions.
         * </p>
         *
         * @return the session lock delay as a duration string (e.g., "5s")
         */
        @Bindable(defaultValue = "5s")
        String getSessionLockDelay();

        /**
         * Gets the time-to-live (TTL) for Consul sessions.
         * <p>
         * This defines how long a session remains valid without renewal.
         * If a session is not renewed within this time, Consul will automatically
         * destroy it, releasing any associated locks.
         * </p>
         *
         * @return the session TTL as a duration string (e.g., "15s")
         */
        @Bindable(defaultValue = "15s")
        String getSessionTtl();

        /**
         * Gets the delay between session renewal attempts.
         * <p>
         * The leader must periodically renew its session to maintain leadership.
         * This setting controls how frequently renewal attempts are made.
         * The delay should be significantly shorter than the session TTL.
         * </p>
         *
         * @return the duration between session renewal attempts
         */
        @Bindable(defaultValue = "10s")
        Duration getSessionRenewalDelay();

        /**
         * Gets the maximum number of retry attempts for operations.
         * <p>
         * When operations fail (such as acquiring leadership or renewing sessions),
         * the system will retry up to this many times before giving up. This setting
         * helps ensure resilience in the face of transient network issues or
         * temporary Consul unavailability.
         * </p>
         *
         * @return the maximum number of retry attempts (must be at least 1)
         */
        @Min(1)
        @Bindable(defaultValue = "3")
        Integer getMaxRetryAttempts();

        /**
         * Gets the delay in milliseconds between retry attempts.
         * <p>
         * When operations fail, this setting controls how long to wait
         * before attempting the operation again. This helps prevent
         * overwhelming the Consul server with rapid retry attempts.
         * </p>
         *
         * @return the retry delay in milliseconds (must be positive)
         */
        @Positive
        @Bindable(defaultValue = "500")
        Integer getRetryDelayMs();

        /**
         * Gets the timeout in milliseconds for Consul operations.
         * <p>
         * This setting defines the maximum time to wait for Consul operations
         * to complete before considering them failed. This prevents operations
         * from hanging indefinitely.
         * </p>
         *
         * @return the operation timeout in milliseconds (must be positive)
         */
        @Positive
        @Bindable(defaultValue = "3000")
        Integer getTimeoutMs();
    }

}
