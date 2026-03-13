package it.polimi.ingsoft.mesos.server.model.ranking;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankingInputValidatorTest {

    private final RankingInputValidator validator = new RankingInputValidator(2, 5);

    @Test
    void normalizesNicknameAndGameId() {
        assertEquals("Alice", this.validator.normalizeNickname("  Alice  "));
        assertEquals("game-1", this.validator.normalizeGameId("  game-1  "));
    }

    @Test
    void validatesPlayedAtAndPlayerCount() {
        Instant now = Instant.now();

        assertEquals(now, this.validator.normalizePlayedAt(now));
        assertEquals(2, this.validator.validatePlayerCount(2));
        assertEquals(5, this.validator.validatePlayerCount(5));
        assertThrows(IllegalArgumentException.class, () -> this.validator.validatePlayerCount(1));
        assertThrows(IllegalArgumentException.class, () -> this.validator.validatePlayerCount(6));
    }

    @Test
    void normalizesScoresAndRejectsDuplicates() {
        List<MatchScore> normalized = this.validator.normalizeScores(
                List.of(new MatchScore(" Alice ", 10), new MatchScore("Bob", 8)),
                2
        );

        assertEquals("Alice", normalized.get(0).nickname());
        assertThrows(IllegalArgumentException.class, () -> this.validator.normalizeScores(
                List.of(new MatchScore("Alice", 10), new MatchScore("Alice", 8)),
                2
        ));
    }
}
