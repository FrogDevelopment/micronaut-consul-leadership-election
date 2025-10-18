package com.frogdevelopment.micronaut.consul.leadership.client;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

import com.frogdevelopment.micronaut.consul.leadership.LeadershipConfiguration;

import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.consul.client.v1.ConsulClient;
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockedQueries;
import io.micronaut.discovery.consul.client.v1.blockingqueries.BlockingQueriesConfiguration;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import reactor.core.publisher.Mono;

/**
 * HTTP client interface for interacting with Consul's session and key-value APIs
 * to implement distributed leadership election.
 * <p>
 * This client provides methods for managing Consul sessions and performing
 * leadership election operations using Consul's key-value store with session locks.
 * The interface uses reactive programming with Mono return types for non-blocking operations.
 * </p>
 *
 * @see <a href="https://developer.hashicorp.com/consul/docs/automate/application-leader-election">Consul - Application leader election</a>
 * @since 1.0.0
 */
@BlockedQueries
@ConsulLeadershipAuth
@Requires(beans = LeadershipConfiguration.class)
@Client(id = ConsulClient.SERVICE_ID, path = "/v1", configuration = BlockingQueriesConfiguration.class)
public interface ConsulLeadershipClient {

    // SESSION

    /**
     * Creates a new Consul session for leadership election.
     * <p>
     * Sessions in Consul are used to associate locks with a specific client instance.
     * When a session is created, it can be used to acquire locks on keys in the KV store.
     * If the session expires or is destroyed, any associated locks are automatically released.
     * </p>
     *
     * @param newSession the session configuration including TTL, lock delay, and behavior
     * @return a Mono containing the created session with its assigned ID
     */
    @Put(value = "/session/create", processes = MediaType.APPLICATION_JSON, single = true)
    Mono<Session> createSession(@Body Session newSession);

    /**
     * Renews an existing Consul session to extend its lifetime.
     * <p>
     * Sessions must be periodically renewed to prevent them from expiring.
     * This method extends the session's TTL, allowing it to continue holding
     * any acquired locks.
     * </p>
     *
     * @param sessionId the ID of the session to renew
     * @return a Mono that completes when the session is successfully renewed
     */
    @Put(value = "/session/renew/{sessionId}", processes = MediaType.APPLICATION_JSON, single = true)
    Mono<Void> renewSession(@PathVariable("sessionId") String sessionId);

    /**
     * Destroys a Consul session, releasing any associated locks.
     * <p>
     * When a session is destroyed, Consul automatically releases any locks
     * that were acquired using that session. This is typically called when
     * an instance is shutting down or voluntarily releasing leadership.
     * </p>
     *
     * @param sessionId the ID of the session to destroy
     * @return a Mono that completes when the session is successfully destroyed
     */
    @Put(value = "/session/destroy/{sessionId}", processes = MediaType.APPLICATION_JSON, single = true)
    Mono<Void> destroySession(@PathVariable("sessionId") String sessionId);

    // ELECTION

    /**
     * Attempts to acquire leadership by creating a lock on the specified key.
     * <p>
     * This method tries to acquire a lock on the given key using the provided session.
     * If successful, the instance becomes the leader. The value parameter typically
     * contains information about the leader (e.g., instance details).
     * </p>
     *
     * @param key       the Consul KV key to lock for leadership
     * @param value     the leadership information to store (typically instance details)
     * @param sessionId the session ID to use for acquiring the lock
     * @return a Mono containing true if leadership was acquired, false otherwise
     */
    @Put(value = "/kv/{key}", processes = MediaType.APPLICATION_JSON, single = true)
    Mono<Boolean> acquireLeadership(@PathVariable("key") String key, @Body Object value,
                                    @NotBlank @QueryValue("acquire") String sessionId);

    /**
     * Releases leadership by removing the lock on the specified key.
     * <p>
     * This method voluntarily releases leadership by removing the lock
     * held by the specified session. This allows other instances to
     * compete for leadership.
     * </p>
     *
     * @param key the Consul KV key to unlock
     * @param value the leadership information to store during release
     * @param sessionId the session ID that currently holds the lock
     * @return a Mono that completes when leadership is successfully released
     */
    @Put(value = "/kv/{key}", processes = MediaType.APPLICATION_JSON, single = true)
    Mono<Void> releaseLeadership(@PathVariable("key") String key, @Body Object value,
                                 @NotBlank @QueryValue("release") String sessionId);

    /**
     * Reads the current leadership information from the specified key.
     * <p>
     * This method retrieves the current state of the leadership key,
     * including information about who holds the lock (if anyone) and
     * the modify index for change detection.
     * </p>
     *
     * @param key the Consul KV key to read
     * @return a Mono containing a list of KeyValue objects with leadership information
     */
    @Get(value = "/kv/{key}", processes = MediaType.APPLICATION_JSON, single = true)
    Mono<List<KeyValue>> readLeadership(@PathVariable("key") String key);

    /**
     * Watches for changes to leadership information using Consul's blocking queries.
     * <p>
     * This method uses Consul's blocking query feature to efficiently wait for
     * changes to the leadership key. It will block until the modify index changes
     * from the provided index value, indicating that leadership has changed.
     * </p>
     *
     * @param key the Consul KV key to watch
     * @param index the modify index to wait for changes from (null for immediate return)
     * @return a Mono containing updated leadership information when changes occur
     */
    @Get(uri = "/kv/{key}", processes = MediaType.APPLICATION_JSON, single = true)
    Mono<List<KeyValue>> watchLeadership(@PathVariable("key") String key,
                                         @QueryValue("index") Integer index);
}
