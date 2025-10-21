package com.frogdevelopment.micronaut.consul.leadership.status;

import lombok.RequiredArgsConstructor;

import java.util.Map;

import com.frogdevelopment.micronaut.consul.leadership.details.LeadershipDetails;

import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;

/**
 * Management endpoint for exposing leadership election status information.
 * <p>
 * This endpoint is available at {@code /leadership} and provides real-time information
 * about the current leadership state of the application instance, including whether
 * this instance is the leader and detailed information about the current leader.
 * </p>
 * <p>
 * The endpoint is useful for monitoring and health checking purposes, allowing
 * external systems to determine the leadership status of each instance in a
 * distributed deployment.
 * </p>
 *
 * @since 1.0.0
 */
@Endpoint(id = "leadership")
@RequiredArgsConstructor
public class LeadershipStatusEndpoint {

    private final LeadershipStatus leadershipStatus;

    /**
     * Returns the current leadership status and details.
     * <p>
     * This method provides a map containing:
     * </p>
     * <ul>
     *   <li>{@code isLeader} - a boolean indicating if this instance is currently the leader</li>
     *   <li>{@code details} - the {@link LeadershipDetails}
     *       object containing information about the current leader</li>
     * </ul>
     *
     * @return a map with leadership status information
     */
    @Read(description = "Return leadership full details")
    public Map<String, Object> leadershipStatus() {
        return Map.of(
                "isLeader", leadershipStatus.isLeader(),
                "details", leadershipStatus.geLeadershipInfo()
        );
    }

}
