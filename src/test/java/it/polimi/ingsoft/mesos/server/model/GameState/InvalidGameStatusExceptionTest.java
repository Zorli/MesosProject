package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvalidGameStatusExceptionTest {

    @Test
    void constructorBuildsMessageWithActionAndStatus() {
        InvalidGameStatusException exception =
                new InvalidGameStatusException("setup", GameStatus.IN_PROGRESS);

        assertEquals(
                "Action setup is not allowed when game status is IN_PROGRESS",
                exception.getMessage()
        );
    }
}
