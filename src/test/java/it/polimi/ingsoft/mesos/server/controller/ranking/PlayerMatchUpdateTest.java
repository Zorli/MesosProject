package it.polimi.ingsoft.mesos.server.controller.ranking;

import it.polimi.ingsoft.mesos.server.controller.ranking.PlayerMatchUpdate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerMatchUpdateTest {

    @Test
    void storesAllValues() {
        PlayerMatchUpdate update = new PlayerMatchUpdate(
                "Alice",
                30,
                1,
                1000,
                18,
                1018,
                12,
                8,
                3
        );

        assertEquals("Alice", update.nickname());
        assertEquals(30, update.finalScore());
        assertEquals(1, update.placement());
        assertEquals(1000, update.eloBefore());
        assertEquals(18, update.eloDelta());
        assertEquals(1018, update.eloAfter());
        assertEquals(12, update.gamesAfter());
        assertEquals(8, update.winsAfter());
        assertEquals(3, update.winStreakAfter());
    }
}
