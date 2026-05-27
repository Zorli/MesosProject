package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;

/**
 * Maps errors caught at the network boundary to protocol error payloads.
 * This keeps TCP and RMI aligned when a client message cannot be accepted
 * before reaching the application/domain logic.
 */

public final class ProtocolErrorMapper {
    private ProtocolErrorMapper() {
    }

    public static ErrorPayload toErrorPayload(RuntimeException exception) {
        if (exception instanceof IllegalArgumentException) {
            return new ErrorPayload("INVALID_MESSAGE", exception.getMessage());
        }
        if (exception instanceof UnsupportedOperationException) {
            return new ErrorPayload("INVALID_MESSAGE", exception.getMessage());
        }
        return new ErrorPayload("INTERNAL_ERROR", "Unexpected server error");
    }
}
