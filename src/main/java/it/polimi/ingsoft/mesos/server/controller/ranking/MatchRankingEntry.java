package it.polimi.ingsoft.mesos.server.controller.ranking;

/**
 * Immutable per-player ranking row stored for one historical match.
 *
 * @param nickname player nickname
 * @param finalScore final match score
 * @param placement final placement in the match (1-based)
 * @param eloBefore Elo before the match
 * @param eloDelta Elo variation introduced by the match
 * @param eloAfter Elo after the match
 * @param winStreakAfter win streak after this match
 */
public record MatchRankingEntry(String nickname,
                                int finalScore,
                                int placement,
                                int eloBefore,
                                int eloDelta,
                                int eloAfter,
                                int winStreakAfter) {

    /**
     * Validates ranking row values.
     */
    public MatchRankingEntry {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("Nickname cannot be null or blank");
        }

        if (finalScore < 0) {
            throw new IllegalArgumentException("Final score cannot be negative");
        }

        if (placement < 1) {
            throw new IllegalArgumentException("Placement must be >= 1");
        }

        if (eloBefore < 0) {
            throw new IllegalArgumentException("Elo before cannot be negative");
        }

        if (eloAfter < 0) {
            throw new IllegalArgumentException("Elo after cannot be negative");
        }

        if (winStreakAfter < 0) {
            throw new IllegalArgumentException("Win streak after cannot be negative");
        }
    }
}
