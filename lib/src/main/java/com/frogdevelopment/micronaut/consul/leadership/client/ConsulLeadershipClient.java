package com.frogdevelopment.micronaut.consul.leadership.client;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;

import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.consul.client.v1.ConsulClient;
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockedQueries;
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockingQueriesConfiguration;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import reactor.core.publisher.Mono;

/**
 * @see <a href="https://developer.hashicorp.com/consul/docs/automate/application-leader-election">Consul - Application leader election</a>
 */
@BlockedQueries
@ConsulLeadershipAuth
@Requires(beans = LeadershipConfiguration.class)
@Client(id = ConsulClient.SERVICE_ID, path = "/v1", configuration = BlockingQueriesConfiguration.class)
public interface ConsulLeadershipClient {

    // SESSION
    @Put(value = "/session/create")
    Mono<Session> createSession(@Body Session newSession);

    @Put(value = "/session/renew/{sessionId}")
    Mono<Void> renewSession(@PathVariable("sessionId") String sessionId);

    @Put(value = "/session/destroy/{sessionId}")
    Mono<Void> destroySession(@PathVariable("sessionId") String sessionId);

    // ELECTION
    @Put(value = "/kv/{key}")
    Mono<Boolean> acquireLeadership(@PathVariable("key") String key, @Body Object value,
                                    @NotBlank @QueryValue("acquire") String sessionId);

    @Put(value = "/kv/{key}")
    Mono<Void> releaseLeadership(@PathVariable("key") String key, @Body Object value,
                                 @NotBlank @QueryValue("release") String sessionId);

    @Get(value = "/kv/{key}")
    Mono<List<KeyValue>> readLeadership(@PathVariable("key") String key);

    @Get(uri = "/kv/{key}", single = true)
    Mono<List<KeyValue>> watchLeadership(@PathVariable("key") String key,
                                         @QueryValue("index") Integer index);

    @Get(uri = "/kv/{key}", single = true)
    Mono<List<KeyValue>> getLeadershipInfo(@PathVariable("key") String key);
}
