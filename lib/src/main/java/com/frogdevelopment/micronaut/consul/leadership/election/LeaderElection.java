package com.frogdevelopment.micronaut.consul.leadership.election;

/**
 * Interface for managing leadership election in a distributed system using Consul.
 * <p>
 * This interface provides the core functionality for participating in a leader election
 * process, where multiple instances compete to become the leader. Only one instance
 * can be the leader at any given time.
 * </p>
 *
 * @since 1.0.0
 */
public interface LeaderElection {

    /**
     * Starts the leader election process.
     * <p>
     * This method initiates the election process where the instance will attempt to
     * acquire leadership. If successful, the instance becomes the leader and will
     * maintain leadership through session renewal. If unsuccessful, the instance
     * will watch for leadership changes and attempt to acquire leadership when
     * it becomes available.
     * </p>
     */
    void start();

    /**
     * Stops the leader election process and releases any held leadership.
     * <p>
     * If this instance is currently the leader, it will gracefully release the
     * leadership and clean up associated resources (sessions, watchers, etc.).
     * After calling this method, the instance will no longer participate in
     * the election process.
     * </p>
     */
    void stop();
}
