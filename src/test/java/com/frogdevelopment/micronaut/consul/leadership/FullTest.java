package com.frogdevelopment.micronaut.consul.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.frogdevelopment.micronaut.consul.leadership.status.LeadershipStatus;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;

//@Disabled
// - start a Consul testcontainer
// - start at least 3 Micronaut servers (same name) who are going to participate to election
// - assert 1 got elected
// - restart leader server
// - assert a new leader is elected
// - assert that leadership changes were correctly propagated among all servers
@Testcontainers
class FullTest {

    @Container
    public static final ConsulContainer CONSUL = new ConsulContainer("hashicorp/consul:1.21.5");

    private EmbeddedServer server1;
    private EmbeddedServer server2;
    private EmbeddedServer server3;

    private static EmbeddedServer createServer(final HashMap<String, Object> properties) {
        return ApplicationContext.run(EmbeddedServer.class, properties);
    }

    @BeforeAll
    static void setup() {
        CONSUL.start();
    }

    @AfterAll
    static void teardown() {
        CONSUL.stop();
    }

    @AfterEach
    void afterEach() {
        if (server1 != null) {
            server1.stop();
        }
        if (server2 != null) {
            server2.stop();
        }
        if (server3 != null) {
            server3.stop();
        }
    }

    @Test
    void should_elect_leadership() {
        // given
        final var properties = new HashMap<String, Object>();
        properties.put("micronaut.application.name", "my-application");
        properties.put("consul.client.host", CONSUL.getHost());
        properties.put("consul.client.port", String.valueOf(CONSUL.getMappedPort(8500)));
        properties.put("consul.leadership.pod-label.enabled", "true");
        properties.put("mock.namespace", "full-test");
        properties.put("mock.cluster", "oz");

        properties.put("hostname", "server_1");
        server1 = createServer(properties);
        assertThat(server1).isNotNull();
        final var leadershipStatus1 = server1.getApplicationContext().getBean(LeadershipStatus.class);
        assertThat(leadershipStatus1).isNotNull();

        await().until(leadershipStatus1::isLeader);

        properties.put("hostname", "server_2");
        server2 = createServer(properties);
        assertThat(server2).isNotNull();
        final var leadershipStatus2 = server2.getApplicationContext().getBean(LeadershipStatus.class);
        assertThat(leadershipStatus2).isNotNull();
        await().until(() -> leadershipStatus2.geLeadershipInfo() != null);
        assertThat(leadershipStatus2.isLeader()).isFalse();

        properties.put("hostname", "server_3");
        server3 = createServer(properties);
        assertThat(server3).isNotNull();
        final var leadershipStatus3 = server3.getApplicationContext().getBean(LeadershipStatus.class);
        assertThat(leadershipStatus3).isNotNull();
        await().until(() -> leadershipStatus3.geLeadershipInfo() != null);
        assertThat(leadershipStatus3.isLeader()).isFalse();

        // assert that all have the same leadership information
        assertThat(leadershipStatus1.geLeadershipInfo()).isEqualTo(leadershipStatus2.geLeadershipInfo());
        assertThat(leadershipStatus1.geLeadershipInfo()).isEqualTo(leadershipStatus3.geLeadershipInfo());
        final var previousLeadershipStatus = leadershipStatus1.geLeadershipInfo();

        // assert that a new leader will be elected
        server1.stop();
        await().atMost(Duration.ofMillis(500))
                .until(() -> leadershipStatus2.isLeader()
                             || leadershipStatus3.isLeader());

        assertThat(leadershipStatus2.geLeadershipInfo()).isEqualTo(leadershipStatus3.geLeadershipInfo());
        assertThat(leadershipStatus2.geLeadershipInfo()).isNotEqualTo(previousLeadershipStatus);

        // assert that previous leader doesn't get back the leadership
        server1.start();
        final var newLeadershipStatus1 = server1.getApplicationContext().getBean(LeadershipStatus.class);
        assertThat(newLeadershipStatus1).isNotNull();
        await().until(() -> newLeadershipStatus1.geLeadershipInfo() != null);
        assertThat(newLeadershipStatus1.isLeader()).isFalse();
        assertThat(newLeadershipStatus1.geLeadershipInfo()).isEqualTo(leadershipStatus2.geLeadershipInfo());
        assertThat(newLeadershipStatus1.geLeadershipInfo()).isEqualTo(leadershipStatus3.geLeadershipInfo());
    }

}
