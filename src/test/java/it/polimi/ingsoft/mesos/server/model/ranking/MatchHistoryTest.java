package it.polimi.ingsoft.mesos.server.model.ranking;

import it.polimi.ingsoft.mesos.server.model.ranking.MatchHistory;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchRankingEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchHistoryTest {

    @Test
    void createsMatchHistoryAndDefensivelyCopiesRankingList() {
        ArrayList<MatchRankingEntry> ranking = new ArrayList<>();
        ranking.add(new MatchRankingEntry("Alice", 30, 1, 1000, 18, 1018, 1));
        ranking.add(new MatchRankingEntry("Bob", 20, 2, 1000, -9, 991, 0));

        MatchHistory history = new MatchHistory(
                "game-1",
                Instant.parse("2026-03-12T10:00:00Z"),
                2,
                ranking
        );

        ranking.clear();

        assertEquals("game-1", history.gameId());
        assertEquals(2, history.ranking().size());
    }

    @Test
    void throwsOnInvalidIdentifiersOrTimestampOrPlayerCount() {
        List<MatchRankingEntry> ranking = List.of(
                new MatchRankingEntry("Alice", 30, 1, 1000, 18, 1018, 1),
                new MatchRankingEntry("Bob", 20, 2, 1000, -9, 991, 0)
        );

        assertThrows(IllegalArgumentException.class,
                () -> new MatchHistory("", Instant.parse("2026-03-12T10:00:00Z"), 2, ranking));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchHistory("game-1", null, 2, ranking));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchHistory("game-1", Instant.parse("2026-03-12T10:00:00Z"), 1, ranking));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchHistory("game-1", Instant.parse("2026-03-12T10:00:00Z"), 6, ranking));
    }

    @Test
    void throwsWhenRankingIsNullEmptyOrSizeMismatch() {
        List<MatchRankingEntry> twoPlayersRanking = List.of(
                new MatchRankingEntry("Alice", 30, 1, 1000, 18, 1018, 1),
                new MatchRankingEntry("Bob", 20, 2, 1000, -9, 991, 0)
        );

        assertThrows(IllegalArgumentException.class,
                () -> new MatchHistory("game-1", Instant.parse("2026-03-12T10:00:00Z"), 2, null));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchHistory("game-1", Instant.parse("2026-03-12T10:00:00Z"), 2, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchHistory("game-1", Instant.parse("2026-03-12T10:00:00Z"), 3, twoPlayersRanking));
    }
}
