package it.polimi.ingsoft.mesos.server.model.ranking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SqliteRankingServiceIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsStateAcrossServiceInstancesUsingSameDatabaseFile() {
        Path dbFile = this.tempDir.resolve("ranking-int.db");

        RankingService firstInstance = new SqliteRankingService(dbFile);
        firstInstance.recordMatchAndGetRanking(
                "game-int-1",
                2,
                Instant.parse("2026-03-12T10:00:00Z"),
                "Alice",
                List.of(new MatchScore("Alice", 12), new MatchScore("Bob", 8))
        );

        RankingService secondInstance = new SqliteRankingService(dbFile);
        List<LeaderboardEntry> leaderboard = secondInstance.getLeaderboard();

        assertFalse(leaderboard.isEmpty());
        assertEquals("Alice", leaderboard.get(0).nickname());
        assertEquals(2, secondInstance.getMatchHistory().get(0).playerCount());
    }
}
