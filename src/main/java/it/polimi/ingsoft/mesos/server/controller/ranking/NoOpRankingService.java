package it.polimi.ingsoft.mesos.server.controller.ranking;

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

    /**
     * Prevents external instantiation of the singleton service.
     */
    private NoOpRankingService() {
    }

    /**
     * Pretends user registration succeeded because no persistent ranking backend is active.
     *
     * @param nickname nickname to register
     * @return always {@code true}
     */
    @Override
    public boolean registerUser(String nickname) {
        return true;
    }

    /**
     * Reports all nicknames as available because no persistence layer is tracking them.
     *
     * @param nickname nickname to validate
     * @return always {@code true}
     */
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

    /**
     * Returns an empty leaderboard when ranking persistence is disabled.
     *
     * @return always an empty list
     */
    @Override
    public List<LeaderboardEntry> getLeaderboard() {
        return List.of();
    }

    /**
     * Returns no persisted match history when ranking persistence is disabled.
     *
     * @return always an empty list
     */
    @Override
    public List<MatchHistory> getMatchHistory() {
        return List.of();
    }

    /**
     * Returns no persisted match history for the requested player count.
     *
     * @param playerCount number of players used to filter history
     * @return always an empty list
     */
    @Override
    public List<MatchHistory> getMatchHistory(int playerCount) {
        return List.of();
    }
}
