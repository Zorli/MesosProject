package it.polimi.ingsoft.mesos.server.model.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteRankingServiceTest {

    @TempDir
    Path tempDir;

    private RankingService rankingService;

    @BeforeEach
    void setup() {
        Path dbFile = this.tempDir.resolve("ranking-test.db");
        this.rankingService = new SqliteRankingService(dbFile);
    }

    @Test
    void registerUserReturnsFalseWhenNicknameAlreadyExists() {
        boolean firstRegistration = this.rankingService.registerUser("Alice");
        boolean secondRegistration = this.rankingService.registerUser("Alice");

        assertTrue(firstRegistration);
        assertFalse(secondRegistration);
        assertFalse(this.rankingService.isNicknameAvailable("Alice"));
    }

    @Test
    void leaderboardIsEmptyBeforeAnyMatch() {
        assertTrue(this.rankingService.getLeaderboard().isEmpty());
    }

    @Test
    void registerUserThrowsOnBlankNickname() {
        assertThrows(IllegalArgumentException.class, () -> this.rankingService.registerUser("   "));
    }

    @Test
    void recordMatchReturnsGlobalEloLeaderboardAndPlayerRank() {
        RankingResult result = this.rankingService.recordMatchAndGetRanking(
                "game-1",
                2,
                Instant.parse("2026-03-12T10:00:00Z"),
                "Alice",
                List.of(new MatchScore("Alice", 12), new MatchScore("Bob", 8))
        );

        assertEquals(1, result.playerRank());
        assertEquals(2, result.leaderboard().size());
        assertEquals("Alice", result.leaderboard().get(0).nickname());
        assertTrue(result.leaderboard().get(0).elo() > 1000);
        assertEquals("Bob", result.leaderboard().get(1).nickname());
        assertTrue(result.leaderboard().get(1).elo() < 1000);
    }

    @Test
    void playerCountMustBeBetweenTwoAndFive() {
        assertThrows(IllegalArgumentException.class, () -> this.rankingService.recordMatchAndGetRanking(
                "game-one-player",
                1,
                Instant.parse("2026-03-12T14:05:00Z"),
                "Alice",
                List.of(new MatchScore("Alice", 10))
        ));

        assertThrows(IllegalArgumentException.class, () -> this.rankingService.recordMatchAndGetRanking(
                "game-six-players",
                6,
                Instant.parse("2026-03-12T14:10:00Z"),
                "Alice",
                List.of(
                        new MatchScore("Alice", 10),
                        new MatchScore("Bob", 9),
                        new MatchScore("Carol", 8),
                        new MatchScore("Dave", 7),
                        new MatchScore("Erin", 6),
                        new MatchScore("Frank", 5)
                )
        ));

        assertThrows(IllegalArgumentException.class, () -> this.rankingService.getMatchHistory(1));
        assertThrows(IllegalArgumentException.class, () -> this.rankingService.getMatchHistory(6));
    }

    @Test
    void consecutiveWinsIncreaseWinStreakAndApplyBonus() {
        this.rankingService.recordMatchAndGetRanking(
                "game-streak-1",
                2,
                Instant.parse("2026-03-12T15:00:00Z"),
                "Alice",
                List.of(new MatchScore("Alice", 20), new MatchScore("Bob", 10))
        );

        this.rankingService.recordMatchAndGetRanking(
                "game-streak-2",
                2,
                Instant.parse("2026-03-12T15:30:00Z"),
                "Alice",
                List.of(new MatchScore("Alice", 22), new MatchScore("Carol", 11))
        );

        List<LeaderboardEntry> leaderboard = this.rankingService.getLeaderboard();
        LeaderboardEntry aliceEntry = leaderboard.stream()
                .filter(entry -> entry.nickname().equals("Alice"))
                .findFirst()
                .orElseThrow();

        assertTrue(aliceEntry.elo() > 1000);
        assertEquals(2, aliceEntry.wins());
        assertEquals(2, aliceEntry.winStreak());
    }

    @Test
    void duplicateGameIdIsRejectedToPreventDoubleCounting() {
        this.rankingService.recordMatchAndGetRanking(
                "game-dup",
                2,
                Instant.parse("2026-03-12T16:00:00Z"),
                "Alice",
                List.of(new MatchScore("Alice", 12), new MatchScore("Bob", 8))
        );

        assertThrows(IllegalStateException.class, () -> this.rankingService.recordMatchAndGetRanking(
                "game-dup",
                2,
                Instant.parse("2026-03-12T16:05:00Z"),
                "Alice",
                List.of(new MatchScore("Alice", 12), new MatchScore("Bob", 8))
        ));

        List<MatchHistory> history = this.rankingService.getMatchHistory();
        assertEquals(1, history.size());
        assertEquals("game-dup", history.get(0).gameId());
    }

    @Test
    void matchHistoryReturnsAllMatchesWithPerMatchOrderAndEloData() {
        this.rankingService.recordMatchAndGetRanking(
                "game-history-1",
                3,
                Instant.parse("2026-03-12T17:00:00Z"),
                "Alice",
                List.of(
                        new MatchScore("Alice", 30),
                        new MatchScore("Bob", 25),
                        new MatchScore("Carol", 25)
                )
        );

        this.rankingService.recordMatchAndGetRanking(
                "game-history-2",
                2,
                Instant.parse("2026-03-12T18:00:00Z"),
                "Dave",
                List.of(new MatchScore("Dave", 10), new MatchScore("Erin", 7))
        );

        List<MatchHistory> history = this.rankingService.getMatchHistory();

        assertEquals(2, history.size());
        assertEquals("game-history-2", history.get(0).gameId());
        assertEquals(2, history.get(0).ranking().size());
        assertEquals(1, history.get(0).ranking().get(0).placement());
        assertTrue(history.get(0).ranking().get(0).eloAfter() >= history.get(0).ranking().get(0).eloBefore());

        assertEquals("game-history-1", history.get(1).gameId());
        assertEquals(3, history.get(1).ranking().size());
        assertEquals(1, history.get(1).ranking().get(0).placement());
        assertEquals(2, history.get(1).ranking().get(1).placement());
        assertEquals(2, history.get(1).ranking().get(2).placement());
    }

    @Test
    void matchHistoryCanBeFilteredByPlayerCount() {
        this.rankingService.recordMatchAndGetRanking(
                "game-filter-2",
                2,
                Instant.parse("2026-03-12T19:00:00Z"),
                "Alice",
                List.of(new MatchScore("Alice", 11), new MatchScore("Bob", 9))
        );

        this.rankingService.recordMatchAndGetRanking(
                "game-filter-3",
                3,
                Instant.parse("2026-03-12T19:30:00Z"),
                "Carol",
                List.of(
                        new MatchScore("Carol", 16),
                        new MatchScore("Dave", 14),
                        new MatchScore("Erin", 12)
                )
        );

        List<MatchHistory> twoPlayersHistory = this.rankingService.getMatchHistory(2);
        List<MatchHistory> threePlayersHistory = this.rankingService.getMatchHistory(3);

        assertEquals(1, twoPlayersHistory.size());
        assertEquals("game-filter-2", twoPlayersHistory.get(0).gameId());
        assertEquals(2, twoPlayersHistory.get(0).playerCount());

        assertEquals(1, threePlayersHistory.size());
        assertEquals("game-filter-3", threePlayersHistory.get(0).gameId());
        assertEquals(3, threePlayersHistory.get(0).playerCount());
    }
}
