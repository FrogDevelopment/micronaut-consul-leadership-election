package com.frogdevelopment.micronaut.consul.leadership.client;

import lombok.RequiredArgsConstructor;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;

import io.micronaut.discovery.consul.client.v1.ConsulAslTokenFilter;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;

@ClientFilter
@ConsulLeadershipAuth
@RequiredArgsConstructor
public class ConsulLeadershipAuthFilter {

    private final LeadershipConfiguration configuration;

    /**
     * Filters HTTP requests by adding a Consul token header if available.
     *
     * @param request The mutable HTTP request to modify
     */
    @RequestFilter
    void filterRequest(final MutableHttpRequest<?> request) {
        configuration.getToken().ifPresent(token -> request.header(ConsulAslTokenFilter.HEADER_CONSUL_TOKEN, token));
    }
}
