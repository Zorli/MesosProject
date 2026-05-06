package it.polimi.ingsoft.mesos.server.model.ranking;

import it.polimi.ingsoft.mesos.server.model.ranking.*;
import it.polimi.ingsoft.mesos.server.testing.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;


@Tag("integration")

/**
 * Integration tests verifying ranking persistence across MySQL-backed service instances.
 */
class MySqlRankingServiceIntegrationTest extends MySqlIntegrationTestSupport {

    @Test
    void persistsStateAcrossServiceInstancesUsingSameDatabase() {
        MySqlConnectionConfig connectionConfig = this.createIsolatedConnectionConfig("ranking_integration");

        RankingService firstInstance = new MySqlRankingService(connectionConfig);
        firstInstance.recordMatchAndGetRanking(
                "game-int-1",
                2,
                Instant.parse("2026-03-12T10:00:00Z"),
                "Alice",
                List.of(new MatchScore("Alice", 12), new MatchScore("Bob", 8))
        );

        RankingService secondInstance = new MySqlRankingService(connectionConfig);
        List<LeaderboardEntry> leaderboard = secondInstance.getLeaderboard();

        assertFalse(leaderboard.isEmpty());
        assertEquals("Alice", leaderboard.get(0).nickname());
        assertEquals(2, secondInstance.getMatchHistory().get(0).playerCount());
    }

    @Test
    void duplicateMatchIsReturnedConsistentlyAcrossServiceInstances() {
        MySqlConnectionConfig connectionConfig = this.createIsolatedConnectionConfig("ranking_integration_dup");

        RankingService writer = new MySqlRankingService(connectionConfig);
        RankingResult firstResult = writer.recordMatchAndGetRanking(
                "game-int-dup",
                2,
                Instant.parse("2026-03-12T11:00:00Z"),
                "Alice",
                List.of(new MatchScore("Alice", 12), new MatchScore("Bob", 8))
        );

        RankingService reader = new MySqlRankingService(connectionConfig);
        RankingResult secondResult = reader.recordMatchAndGetRanking(
                "game-int-dup",
                2,
                Instant.parse("2026-03-12T11:10:00Z"),
                "Alice",
                List.of(new MatchScore("Alice", 5), new MatchScore("Bob", 1))
        );

        assertEquals(firstResult.playerRank(), secondResult.playerRank());
        assertEquals(firstResult.leaderboard().get(0), secondResult.leaderboard().get(0));
        assertEquals(firstResult.leaderboard().get(1), secondResult.leaderboard().get(1));
    }
}
