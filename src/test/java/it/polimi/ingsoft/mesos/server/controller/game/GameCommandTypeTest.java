package it.polimi.ingsoft.mesos.server.controller.game;

import it.polimi.ingsoft.mesos.server.controller.game.enums.GameCommandType;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.ChooseCardPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.ExtraDrawPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.MoveTotemPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link GameCommandType}.
 *
 * <p>The enum is the protocol boundary that classifies client payloads before
 * {@link GameController} delegates them to the model.
 */
class GameCommandTypeTest {

    /**
     * Every supported game payload maps to exactly one game command.
     */
    @Test
    void supportedPayloadsMapToTheirCommandType() {
        assertEquals(GameCommandType.CHOOSE_CARD, GameCommandType.fromPayload(new ChooseCardPayload(1)));
        assertEquals(GameCommandType.EXTRA_DRAW, GameCommandType.fromPayload(new ExtraDrawPayload(2)));
        assertEquals(GameCommandType.MOVE_TOTEM, GameCommandType.fromPayload(new MoveTotemPayload("B")));
    }

    /**
     * Null payloads are caller errors and are rejected before pattern matching.
     */
    @Test
    void nullPayloadIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> GameCommandType.fromPayload(null));
    }

    /**
     * Payloads outside the game command set are rejected instead of being
     * silently routed to the wrong action.
     */
    @Test
    void unexpectedPayloadIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> GameCommandType.fromPayload(new UnknownPayload()));
    }

    private static class UnknownPayload implements ClientPayload {
    }
}
