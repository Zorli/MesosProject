package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.server.model.game.CardNotDrawableException;
import it.polimi.ingsoft.mesos.server.model.game.CardNotPresentException;
import it.polimi.ingsoft.mesos.server.model.game.NotPlayersTurnException;
import it.polimi.ingsoft.mesos.server.network.AuthenticationException;
import it.polimi.ingsoft.mesos.shared.payload.server.ApplicationErrorCode;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;

import java.util.Objects;

/**
 * Maps application and protocol exceptions to the transport-independent error contract.
 */
public final class ApplicationErrorMapper {
    public ErrorPayload toPayload(Exception exception) {
        Objects.requireNonNull(exception, "exception cannot be null");
        ApplicationErrorCode code = this.toCode(exception);
        String message = code == ApplicationErrorCode.INTERNAL_ERROR
                ? "Unexpected server error"
                : exception.getMessage();
        return new ErrorPayload(code.name(), message);
    }

    private ApplicationErrorCode toCode(Exception exception) {
        if (exception instanceof AuthenticationException) {
            return ApplicationErrorCode.AUTH_ERROR;
        }
        if (exception instanceof IllegalArgumentException || exception instanceof UnsupportedOperationException) {
            return ApplicationErrorCode.INVALID_MESSAGE;
        }
        if (exception instanceof NotPlayersTurnException) {
            return ApplicationErrorCode.NOT_PLAYERS_TURN;
        }
        if (exception instanceof CardNotDrawableException || exception instanceof CardNotPresentException) {
            return ApplicationErrorCode.CARD_NOT_DRAWABLE;
        }
        if (exception instanceof IllegalStateException) {
            return this.toIllegalStateCode((IllegalStateException) exception);
        }
        return ApplicationErrorCode.INTERNAL_ERROR;
    }

    private ApplicationErrorCode toIllegalStateCode(IllegalStateException exception) {
        String message = exception.getMessage();
        if ("Client is not authenticated".equals(message)) {
            return ApplicationErrorCode.AUTH_ERROR;
        }
        if ("Client is not in a lobby".equals(message)) {
            return ApplicationErrorCode.NOT_IN_LOBBY;
        }
        if ("Lobby is full".equals(message)) {
            return ApplicationErrorCode.LOBBY_FULL;
        }
        return ApplicationErrorCode.INTERNAL_ERROR;
    }
}
