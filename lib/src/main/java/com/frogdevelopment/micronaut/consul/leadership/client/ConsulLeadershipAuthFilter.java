package com.frogdevelopment.micronaut.consul.leadership.client;

import lombok.RequiredArgsConstructor;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;

import io.micronaut.discovery.consul.client.v1.ConsulAslTokenFilter;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;

/**
 * HTTP client filter that automatically adds Consul authentication tokens to requests.
 * <p>
 * This filter intercepts HTTP requests to Consul and adds the authentication token
 * header when a token is configured in the {@link LeadershipConfiguration}. It ensures
 * that all leadership election operations are properly authenticated when Consul's
 * ACL system is enabled.
 * </p>
 * <p>
 * The filter is only applied to clients annotated with {@link ConsulLeadershipAuth}.
 * If no token is configured, requests are sent without authentication.
 * </p>
 *
 * @see ConsulLeadershipAuth
 * @since 1.0.0
 */
@ClientFilter
@ConsulLeadershipAuth
@RequiredArgsConstructor
public class ConsulLeadershipAuthFilter {

    private final LeadershipConfiguration configuration;

    /**
     * Filters HTTP requests by adding a Consul token header if available.
     * <p>
     * If a Consul token is configured in the leadership configuration, this method
     * adds it to the request headers using the standard Consul token header name.
     * This allows the application to authenticate with Consul's ACL system.
     * </p>
     *
     * @param request the mutable HTTP request to modify
     */
    @RequestFilter
    void filterRequest(final MutableHttpRequest<?> request) {
        configuration.getToken().ifPresent(token -> request.header(ConsulAslTokenFilter.HEADER_CONSUL_TOKEN, token));
    }
}
