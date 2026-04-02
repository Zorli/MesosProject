package it.polimi.ingsoft.mesos.server.model.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TileNotAvailableExceptionTest {

    @Test
    void constructorWithIndexBuildsMeaningfulMessage() {
        TileNotAvailableException exception = new TileNotAvailableException(3);

        assertEquals("Selected tile is not available: 3", exception.getMessage());
    }

    @Test
    void constructorWithCodeBuildsMeaningfulMessage() {
        TileNotAvailableException exception = new TileNotAvailableException("C");

        assertEquals("Selected tile is not available: C", exception.getMessage());
    }
}
