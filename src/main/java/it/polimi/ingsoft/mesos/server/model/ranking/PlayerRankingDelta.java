package it.polimi.ingsoft.mesos.server.model.ranking;

/**
 * Ranking movement for one player produced by a completed match.
 *
 * @param nickname player nickname
 * @param oldScore ranking score before the match
 * @param newScore ranking score after the match
 * @param previousGlobalRank global rank before the match
 * @param newGlobalRank global rank after the match
 */
public record PlayerRankingDelta(String nickname,
                                 int oldScore,
                                 int newScore,
                                 int previousGlobalRank,
                                 int newGlobalRank) {
    public PlayerRankingDelta {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("Nickname cannot be null or blank");
        }
        if (oldScore < 0 || newScore < 0) {
            throw new IllegalArgumentException("Ranking scores cannot be negative");
        }
        if (previousGlobalRank < 1 || newGlobalRank < 1) {
            throw new IllegalArgumentException("Global ranks must be >= 1");
        }
    }
}
