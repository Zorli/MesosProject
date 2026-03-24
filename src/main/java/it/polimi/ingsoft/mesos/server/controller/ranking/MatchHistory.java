package it.polimi.ingsoft.mesos.server.controller.ranking;

import java.time.Instant;
import java.util.List;

/**
 * Immutable historical snapshot of one persisted match.
 *
 * @param gameId unique match id
 * @param playedAtUtc match timestamp in UTC
 * @param playerCount number of players in the match (2..5)
 * @param ranking ordered ranking rows for all players in the match
 */
public record MatchHistory(String gameId,
                           Instant playedAtUtc,
                           int playerCount,
                           List<MatchRankingEntry> ranking) {

    /**
     * Validates the history payload and performs a defensive copy of the ranking list.
     */
    public MatchHistory {
        if (gameId == null || gameId.isBlank()) {
            throw new IllegalArgumentException("Game id cannot be null or blank");
        }

        if (playedAtUtc == null) {
            throw new IllegalArgumentException("Played at timestamp cannot be null");
        }

        if (playerCount < 2 || playerCount > 5) {
            throw new IllegalArgumentException("Player count must be between 2 and 5");
        }

        if (ranking == null || ranking.isEmpty()) {
            throw new IllegalArgumentException("Ranking cannot be null or empty");
        }

        if (ranking.size() != playerCount) {
            throw new IllegalArgumentException("Ranking size must match player count");
        }

        ranking = List.copyOf(ranking);
    }
}
