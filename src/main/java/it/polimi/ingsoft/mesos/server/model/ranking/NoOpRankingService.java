package it.polimi.ingsoft.mesos.server.model.ranking;

import java.time.Instant;
import java.util.List;

/**
 * Naive no-database implementation used when ranking persistence is disabled.
 */
public final class NoOpRankingService implements RankingService {
    /**
     * Shared singleton instance.
     */
    public static final NoOpRankingService INSTANCE = new NoOpRankingService();

    private NoOpRankingService() {
    }

    @Override
    public boolean registerUser(String nickname) {
        return true;
    }

    @Override
    public boolean isNicknameAvailable(String nickname) {
        return true;
    }

    @Override
    public RankingResult recordMatchAndGetRanking(String gameId,
                                                  int playerCount,
                                                  Instant playedAtUtc,
                                                  String requestingNickname,
                                                  List<MatchScore> finalScores) {
        throw new IllegalStateException("Ranking persistence is not available in no-db mode");
    }

    @Override
    public List<LeaderboardEntry> getLeaderboard() {
        return List.of();
    }

    @Override
    public List<MatchHistory> getMatchHistory() {
        return List.of();
    }

    @Override
    public List<MatchHistory> getMatchHistory(int playerCount) {
        return List.of();
    }
}
