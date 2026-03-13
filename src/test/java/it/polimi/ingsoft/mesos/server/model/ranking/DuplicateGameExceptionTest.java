package it.polimi.ingsoft.mesos.server.model.ranking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DuplicateGameExceptionTest {

    @Test
    void containsGameIdInErrorMessage() {
        DuplicateGameException exception = new DuplicateGameException("game-42");

        assertEquals("Game already recorded: game-42", exception.getMessage());
    }
}
