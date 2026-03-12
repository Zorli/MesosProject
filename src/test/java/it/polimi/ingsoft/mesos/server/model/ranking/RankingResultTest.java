package it.polimi.ingsoft.mesos.server.model.ranking;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankingResultTest {

    @Test
    void createsRankingResultAndDefensivelyCopiesLeaderboard() {
        ArrayList<LeaderboardEntry> leaderboard = new ArrayList<>();
        leaderboard.add(new LeaderboardEntry("Alice", 1040, 7, 2, 1));
        leaderboard.add(new LeaderboardEntry("Bob", 1005, 4, 0, 2));

        RankingResult result = new RankingResult(1, leaderboard);

        leaderboard.clear();

        assertEquals(1, result.playerRank());
        assertEquals(2, result.leaderboard().size());
    }

    @Test
    void throwsOnInvalidRankOrLeaderboard() {
        List<LeaderboardEntry> leaderboard = List.of(new LeaderboardEntry("Alice", 1040, 7, 2, 1));

        assertThrows(IllegalArgumentException.class, () -> new RankingResult(0, leaderboard));
        assertThrows(IllegalArgumentException.class, () -> new RankingResult(1, null));
        assertThrows(IllegalArgumentException.class, () -> new RankingResult(1, List.of()));
    }
}
