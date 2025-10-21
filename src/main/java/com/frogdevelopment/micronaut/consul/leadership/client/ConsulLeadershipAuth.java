package com.frogdevelopment.micronaut.consul.leadership.client;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.micronaut.http.annotation.FilterMatcher;

/**
 * Filter matcher annotation for Consul leadership authentication.
 * <p>
 * This annotation is used to mark HTTP client interfaces that require
 * Consul authentication. When applied to a client interface, it ensures
 * that the {@link ConsulLeadershipAuthFilter} is applied to all requests,
 * automatically adding the Consul authentication token header when configured.
 * </p>
 * <p>
 * This annotation should be applied to HTTP clients that interact with
 * Consul's API for leadership election operations.
 * </p>
 *
 * @see ConsulLeadershipAuthFilter
 * @since 1.0.0
 */
@Documented
@FilterMatcher
@Target({TYPE})
@Retention(RUNTIME)
public @interface ConsulLeadershipAuth {
}
