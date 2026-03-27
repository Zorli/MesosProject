package it.polimi.ingsoft.mesos.server.model.ranking;

import java.util.List;

/**
 * Result returned after recording a match: requester rank and full leaderboard.
 *
 * @param playerRank global rank of the requesting player
 * @param leaderboard full global leaderboard after the match is recorded
 */
public record RankingResult(int playerRank, List<LeaderboardEntry> leaderboard) {

    /**
     * Validates result payload and performs a defensive copy of the leaderboard list.
     */
    public RankingResult {
        if (playerRank < 1) {
            throw new IllegalArgumentException("Player rank must be >= 1");
        }

        if (leaderboard == null || leaderboard.isEmpty()) {
            throw new IllegalArgumentException("Leaderboard cannot be null or empty");
        }

        leaderboard = List.copyOf(leaderboard);
    }
}
