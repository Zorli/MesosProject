package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;

/**
 * Maps errors caught at the network boundary to protocol error payloads.
 * This keeps TCP and RMI aligned when a client message cannot be accepted
 * before reaching the application/domain logic.
 */

// !!!! QUesto serve per essere condiviso dalle due virtualview che in caso di eccezioni/errori di solo protocollo di connessione
//    si comportano allo stesso modo (osservabilità identica)
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
