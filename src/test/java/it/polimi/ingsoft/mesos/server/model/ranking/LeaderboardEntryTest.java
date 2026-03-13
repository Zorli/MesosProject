package it.polimi.ingsoft.mesos.server.model.ranking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LeaderboardEntryTest {

    @Test
    void createsLeaderboardEntryWithValidInput() {
        LeaderboardEntry entry = new LeaderboardEntry("Alice", 1042, 10, 3, 1);

        assertEquals("Alice", entry.nickname());
        assertEquals(1042, entry.elo());
        assertEquals(10, entry.wins());
        assertEquals(3, entry.winStreak());
        assertEquals(1, entry.rank());
    }

    @Test
    void throwsOnInvalidNickname() {
        assertThrows(IllegalArgumentException.class, () -> new LeaderboardEntry(null, 1000, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new LeaderboardEntry("", 1000, 0, 0, 1));
    }

    @Test
    void throwsOnNegativeCountersOrInvalidRank() {
        assertThrows(IllegalArgumentException.class, () -> new LeaderboardEntry("Alice", -1, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new LeaderboardEntry("Alice", 1000, -1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new LeaderboardEntry("Alice", 1000, 0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new LeaderboardEntry("Alice", 1000, 0, 0, 0));
    }
}
