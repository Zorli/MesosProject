package it.polimi.ingsoft.mesos.server.model.ranking;

import it.polimi.ingsoft.mesos.server.model.ranking.MatchScore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchScoreTest {

    @Test
    void createsMatchScoreWithValidInput() {
        MatchScore matchScore = new MatchScore("Alice", 42);

        assertEquals("Alice", matchScore.nickname());
        assertEquals(42, matchScore.finalScore());
    }

    @Test
    void throwsWhenNicknameIsNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> new MatchScore(null, 10));
        assertThrows(IllegalArgumentException.class, () -> new MatchScore("   ", 10));
    }

    @Test
    void acceptsNegativeScores() {
        MatchScore matchScore = new MatchScore("Alice", -12);

        assertEquals("Alice", matchScore.nickname());
        assertEquals(-12, matchScore.finalScore());
    }
}
