package it.polimi.ingsoft.mesos.server.network.connectionHandling;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;

/**
 * Maps errors caught at the network boundary to protocol error payloads.
 * <p>
 * This class is used by TCP and RMI virtual views when a client message fails
 * before it can be accepted by the application controllers. Its architectural
 * role is to keep transport-level error reporting consistent: both network
 * protocols convert parsing, protocol, or unexpected runtime failures into the
 * same {@link ErrorPayload} format.
 * <p>
 * The mapper only describes failures detected at the network boundary.
 * Application and domain errors are produced later by controllers or models and
 * are not mapped here.
 */
public final class ProtocolErrorMapper {
    /**
     * Prevents instantiation of this stateless utility class.
     */
    private ProtocolErrorMapper() {
    }

    /**
     * Converts a runtime exception raised at the network boundary into an error
     * payload that can be sent to the client.
     * <p>
     * Invalid client input is reported as {@code INVALID_MESSAGE}. Unexpected
     * preLobby-side failures are hidden behind {@code INTERNAL_ERROR}, so clients
     * receive a stable protocol response instead of Java exception details.
     *
     * @param exception exception caught while processing a client message at the
     *                  transport boundary
     * @return protocol error payload to send back to the client
     */
    public static ErrorPayload toErrorPayload(RuntimeException exception) {
        if (exception instanceof IllegalArgumentException) {
            return new ErrorPayload("INVALID_MESSAGE", exception.getMessage());
        }
        if (exception instanceof UnsupportedOperationException) {
            return new ErrorPayload("INVALID_MESSAGE", exception.getMessage());
        }
        return new ErrorPayload("INTERNAL_ERROR", "Unexpected preLobby error");
    }
}
