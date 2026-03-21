package it.polimi.ingsoft.mesos.server.model.ranking;

import it.polimi.ingsoft.mesos.server.testing.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("integration")
class PostgresRankingServiceIntegrationTest extends PostgresIntegrationTestSupport {

    @Test
    void persistsStateAcrossServiceInstancesUsingSameDatabase() {
        PostgresConnectionConfig connectionConfig = this.createIsolatedConnectionConfig("ranking_integration");

        RankingService firstInstance = new PostgresRankingService(connectionConfig);
        firstInstance.recordMatchAndGetRanking(
                "game-int-1",
                2,
                Instant.parse("2026-03-12T10:00:00Z"),
                "Alice",
                List.of(new MatchScore("Alice", 12), new MatchScore("Bob", 8))
        );

        RankingService secondInstance = new PostgresRankingService(connectionConfig);
        List<LeaderboardEntry> leaderboard = secondInstance.getLeaderboard();

        assertFalse(leaderboard.isEmpty());
        assertEquals("Alice", leaderboard.get(0).nickname());
        assertEquals(2, secondInstance.getMatchHistory().get(0).playerCount());
    }
}
