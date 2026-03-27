package it.polimi.ingsoft.mesos.server.model.ranking;

import java.time.Instant;
import java.util.List;

/**
 * Model service that stores completed matches and exposes global Elo ranking data.
 */
public interface RankingService {

    /**
     * Registers a user nickname if it is not already present.
     *
     * @param nickname nickname to register
     * @return {@code true} if inserted, {@code false} if already existing
     */
    boolean registerUser(String nickname);

    /**
     * Checks whether a nickname is currently available.
     *
     * @param nickname nickname to check
     * @return {@code true} if nickname is free, {@code false} otherwise
     */
    boolean isNicknameAvailable(String nickname);

    /**
     * Stores one completed match atomically and returns updated global ranking information.
     *
     * @param gameId unique match id used to prevent duplicate inserts
     * @param playerCount number of match players (2..5)
     * @param playedAtUtc match timestamp in UTC
     * @param requestingNickname nickname for which rank is returned
     * @param finalScores final scores of all players in the match
     * @return rank of requesting player and full leaderboard after insertion
     */
    RankingResult recordMatchAndGetRanking(String gameId,
                                           int playerCount,
                                           Instant playedAtUtc,
                                           String requestingNickname,
                                           List<MatchScore> finalScores);

    /**
     * Returns the full global Elo leaderboard.
     *
     * @return ordered leaderboard rows
     */
    List<LeaderboardEntry> getLeaderboard();

    /**
     * Returns complete match history for all supported player counts.
     *
     * @return historical matches ordered from newest to oldest
     */
    List<MatchHistory> getMatchHistory();

    /**
     * Returns match history filtered by player count.
     *
     * @param playerCount target match size (2..5)
     * @return filtered historical matches ordered from newest to oldest
     */
    List<MatchHistory> getMatchHistory(int playerCount);
}
