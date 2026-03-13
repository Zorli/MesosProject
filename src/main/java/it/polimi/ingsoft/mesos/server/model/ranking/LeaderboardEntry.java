package it.polimi.ingsoft.mesos.server.model.ranking;

/**
 * Immutable row of the global Elo leaderboard.
 *
 * @param nickname player nickname
 * @param elo current global Elo rating
 * @param wins number of wins registered by the player
 * @param winStreak current consecutive wins
 * @param rank computed leaderboard position (1-based)
 */
public record LeaderboardEntry(String nickname, int elo, int wins, int winStreak, int rank) {

    /**
     * Validates leaderboard values.
     */
    public LeaderboardEntry {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("Nickname cannot be null or blank");
        }

        if (elo < 0) {
            throw new IllegalArgumentException("Elo cannot be negative");
        }

        if (wins < 0) {
            throw new IllegalArgumentException("Wins cannot be negative");
        }

        if (winStreak < 0) {
            throw new IllegalArgumentException("Win streak cannot be negative");
        }

        if (rank < 1) {
            throw new IllegalArgumentException("Rank must be >= 1");
        }
    }
}
