package it.polimi.ingsoft.mesos.server.network.exceptions;

/**
 * Exception used to report an authentication failure detected near the network
 * layer.
 * <p>
 * It represents a rejected authentication attempt before the client can be
 * treated as an authenticated player. The exception carries the reason that can
 * later be mapped to an error response by the preLobby-side protocol handling
 * code.
 */
public class AuthenticationException extends RuntimeException {
    /**
     * Creates an authentication exception with a human-readable failure reason.
     *
     * @param message explanation of the authentication failure
     */
    public AuthenticationException(String message) {
        super(message);
    }

    /**
     * Creates an authentication exception with both a failure reason and the
     * original cause.
     *
     * @param message explanation of the authentication failure
     * @param cause exception that caused the authentication failure
     */
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
