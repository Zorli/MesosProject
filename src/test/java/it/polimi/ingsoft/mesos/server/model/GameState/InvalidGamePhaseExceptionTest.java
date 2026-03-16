package it.polimi.ingsoft.mesos.server.model.GameState;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvalidGamePhaseExceptionTest {

    @Test
    void constructorBuildsMessageWithActionAndRoundPhase() {
        InvalidGamePhaseException exception =
                new InvalidGamePhaseException("endRound", RoundPhase.RESOLVING_EVENTS);

        assertEquals(
                "Action endRound is not allowed during round phase RESOLVING_EVENTS",
                exception.getMessage()
        );
    }
}
