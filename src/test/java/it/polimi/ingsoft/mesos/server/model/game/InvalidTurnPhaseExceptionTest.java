package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvalidTurnPhaseExceptionTest {

    @Test
    void constructorBuildsMessageWithActionAndTurnPhase() {
        InvalidTurnPhaseException exception =
                new InvalidTurnPhaseException("chooseCard", TurnPhase.WAITING_FOR_CARD_SELECTION);

        assertEquals(
                "Action chooseCard is not allowed during phase WAITING_FOR_CARD_SELECTION",
                exception.getMessage()
        );
    }
}
