package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.model.ranking.LeaderboardEntry;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchHistory;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchRankingEntry;
import it.polimi.ingsoft.mesos.server.model.ranking.PlayerRankingDelta;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingResult;
import it.polimi.ingsoft.mesos.shared.payload.server.FinalMatchRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.FinalMatchRankingStatus;
import it.polimi.ingsoft.mesos.shared.payload.server.GlobalRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.MatchHistoryPayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingPayloadMapperTest {
    private final RankingPayloadMapper mapper = new RankingPayloadMapper();

    @Test
    void mapsLeaderboardToGlobalRankingPayload() {
        GlobalRankingPayload payload = this.mapper.globalRanking(List.of(
                new LeaderboardEntry("Alice", 1200, 4, 2, 1),
                new LeaderboardEntry("Bob", 980, 1, 0, 2)
        ));

        assertEquals(2, payload.entries.size());
        assertEquals("Alice", payload.entries.getFirst().username);
        assertEquals(1200, payload.entries.getFirst().score);
        assertEquals(1, payload.entries.getFirst().globalRank);
        assertEquals(4, payload.entries.getFirst().wins);
        assertEquals(2, payload.entries.getFirst().winStreak);
    }

    @Test
    void mapsRankingResultToFinalMatchRankingPayload() {
        RankingResult result = new RankingResult(
                1,
                List.of(new LeaderboardEntry("Alice", 1020, 1, 1, 1)),
                List.of(new PlayerRankingDelta("Alice", 1000, 1020, 3, 1))
        );

        FinalMatchRankingPayload payload = this.mapper.finalMatchRanking("game-1", result);

        assertEquals("game-1", payload.gameId);
        assertEquals(FinalMatchRankingStatus.RECORDED, payload.status);
        assertEquals("Alice", payload.deltas.getFirst().username);
        assertEquals(1000, payload.deltas.getFirst().oldScore);
        assertEquals(1020, payload.deltas.getFirst().newScore);
        assertEquals(3, payload.deltas.getFirst().previousGlobalRank);
        assertEquals(1, payload.deltas.getFirst().newGlobalRank);
    }

    @Test
    void mapsMatchHistoryPayload() {
        MatchHistoryPayload payload = this.mapper.history(List.of(
                new MatchHistory(
                        "game-1",
                        Instant.parse("2026-05-06T19:30:00Z"),
                        2,
                        List.of(
                                new MatchRankingEntry("Alice", 30, 1, 1000, 20, 1020, 2, 1, 1),
                                new MatchRankingEntry("Bob", 10, 2, 1000, -10, 990, 5, 5, 0)
                        )
                )
        ));

        assertEquals("game-1", payload.matches.getFirst().gameId);
        assertEquals(2, payload.matches.getFirst().playerCount);
        assertEquals("Alice", payload.matches.getFirst().standings.getFirst().username);
        assertEquals(30, payload.matches.getFirst().standings.getFirst().finalScore);
        assertEquals(1000, payload.matches.getFirst().standings.getFirst().oldScore);
        assertEquals(1020, payload.matches.getFirst().standings.getFirst().newScore);
        assertEquals(1, payload.matches.getFirst().standings.getFirst().previousGlobalRank);
        assertEquals(1, payload.matches.getFirst().standings.getFirst().newGlobalRank);
    }

    @Test
    void createsExplicitDisabledAndFailedFinalRankingPayloads() {
        assertEquals(FinalMatchRankingStatus.DISABLED, this.mapper.rankingDisabled("game-1").status);
        FinalMatchRankingPayload failed = this.mapper.rankingFailed("game-1", "backend down");

        assertEquals(FinalMatchRankingStatus.FAILED, failed.status);
        assertEquals("backend down", failed.message);
        assertTrue(failed.deltas.isEmpty());
    }
}
