package it.polimi.ingsoft.mesos.server.model.ranking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchRankingEntryTest {

    @Test
    void createsMatchRankingEntryWithValidInput() {
        MatchRankingEntry entry = new MatchRankingEntry("Alice", 50, 1, 1000, 20, 1020, 2);

        assertEquals("Alice", entry.nickname());
        assertEquals(50, entry.finalScore());
        assertEquals(1, entry.placement());
        assertEquals(1000, entry.eloBefore());
        assertEquals(20, entry.eloDelta());
        assertEquals(1020, entry.eloAfter());
        assertEquals(2, entry.winStreakAfter());
    }

    @Test
    void throwsOnInvalidNicknameOrPlacement() {
        assertThrows(IllegalArgumentException.class,
                () -> new MatchRankingEntry(null, 10, 1, 1000, 5, 1005, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchRankingEntry("Alice", 10, 0, 1000, 5, 1005, 0));
    }

    @Test
    void throwsOnNegativeScoresOrEloOrStreak() {
        assertThrows(IllegalArgumentException.class,
                () -> new MatchRankingEntry("Alice", -1, 1, 1000, 5, 1005, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchRankingEntry("Alice", 10, 1, -1, 5, 1005, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchRankingEntry("Alice", 10, 1, 1000, 5, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchRankingEntry("Alice", 10, 1, 1000, 5, 1005, -1));
    }
}
