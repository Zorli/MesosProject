package it.polimi.ingsoft.mesos.server.controller.game;

import it.polimi.ingsoft.mesos.server.controller.game.exceptions.MatchFrozenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for {@link MatchFrozenException}.
 */
class MatchFrozenExceptionTest {

    /**
     * The exception exposes the fixed message used by controller code when a
     * frozen match rejects further gameplay commands.
     */
    @Test
    void messageExplainsThatTheMatchIsFrozen() {
        assertEquals("Match is frozen", new MatchFrozenException().getMessage());
    }
}
