package it.polimi.ingsoft.mesos.server.controller.ranking;

import it.polimi.ingsoft.mesos.server.controller.ranking.DuplicateGameException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DuplicateGameExceptionTest {

    @Test
    void containsGameIdInErrorMessage() {
        DuplicateGameException exception = new DuplicateGameException("game-42");

        assertEquals("Game already recorded: game-42", exception.getMessage());
    }
}
