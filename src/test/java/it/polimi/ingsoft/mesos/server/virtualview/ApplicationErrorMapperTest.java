package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.server.model.game.CardNotDrawableException;
import it.polimi.ingsoft.mesos.server.model.game.NotPlayersTurnException;
import it.polimi.ingsoft.mesos.server.network.AuthenticationException;
import it.polimi.ingsoft.mesos.shared.payload.server.ApplicationErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationErrorMapperTest {
    private final ApplicationErrorMapper mapper = new ApplicationErrorMapper();

    @Test
    void mapsProtocolErrorsToStableCodes() {
        assertEquals(
                ApplicationErrorCode.AUTH_ERROR.name(),
                this.mapper.toPayload(new AuthenticationException("Client is not authenticated")).code
        );
        assertEquals(
                ApplicationErrorCode.INVALID_MESSAGE.name(),
                this.mapper.toPayload(new IllegalArgumentException("Malformed command")).code
        );
    }

    @Test
    void mapsExpectedDomainIllegalStateWithoutInternalErrorFallback() {
        assertEquals(
                ApplicationErrorCode.NOT_IN_LOBBY.name(),
                this.mapper.toPayload(new IllegalStateException("Client is not in a lobby")).code
        );
        assertEquals(
                ApplicationErrorCode.LOBBY_FULL.name(),
                this.mapper.toPayload(new IllegalStateException("Lobby is full")).code
        );
    }

    @Test
    void mapsGameDomainErrorsToSharedCodes() {
        assertEquals(
                ApplicationErrorCode.NOT_PLAYERS_TURN.name(),
                this.mapper.toPayload(new NotPlayersTurnException(null, null)).code
        );
        assertEquals(
                ApplicationErrorCode.CARD_NOT_DRAWABLE.name(),
                this.mapper.toPayload(new CardNotDrawableException(null, null)).code
        );
    }
}
