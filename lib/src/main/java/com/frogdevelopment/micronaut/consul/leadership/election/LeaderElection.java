package com.frogdevelopment.micronaut.consul.leadership.election;

public interface LeaderElection {

    boolean isLeader();

    void start();

    void stop();
}
