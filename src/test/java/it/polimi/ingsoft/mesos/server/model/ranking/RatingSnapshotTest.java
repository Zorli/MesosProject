package it.polimi.ingsoft.mesos.server.model.ranking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RatingSnapshotTest {

    @Test
    void storesAllValues() {
        RatingSnapshot snapshot = new RatingSnapshot("Alice", 1010, 7, 4, 2);

        assertEquals("Alice", snapshot.nickname());
        assertEquals(1010, snapshot.elo());
        assertEquals(7, snapshot.gamesPlayed());
        assertEquals(4, snapshot.wins());
        assertEquals(2, snapshot.winStreak());
    }
}
