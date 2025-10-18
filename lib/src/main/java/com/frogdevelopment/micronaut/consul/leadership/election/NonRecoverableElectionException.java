package com.frogdevelopment.micronaut.consul.leadership.election;

/**
 * Exception thrown when a non-recoverable error occurs during the leadership election process.
 * <p>
 * This exception indicates a critical failure that prevents the leadership election
 * from continuing, such as inability to create sessions, parse leadership information,
 * or communicate with Consul. When this exception is thrown, the election process
 * typically cannot recover automatically and requires intervention or restart.
 * </p>
 *
 * @since 1.0.0
 */
class NonRecoverableElectionException extends RuntimeException {

    /**
     * Constructs a new non-recoverable election exception with the specified detail message.
     *
     * @param message the detail message explaining the reason for the exception
     */
    public NonRecoverableElectionException(final String message) {
        super(message);
    }

    /**
     * Constructs a new non-recoverable election exception with the specified detail message and cause.
     *
     * @param message the detail message explaining the reason for the exception
     * @param cause   the underlying cause of the exception
     */
    public NonRecoverableElectionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
