package com.frogdevelopment.micronaut.consul.leadership.election;

class NonRecoverableElectionException extends RuntimeException {

    public NonRecoverableElectionException(final String message) {
        super(message);
    }

    public NonRecoverableElectionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
