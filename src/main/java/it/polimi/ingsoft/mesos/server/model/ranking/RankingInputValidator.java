package it.polimi.ingsoft.mesos.server.model.ranking;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Normalizes and validates ranking input values before database operations.
 */
final class RankingInputValidator {
    private static final int MAX_NICKNAME_LENGTH = 255;
    private static final int MAX_GAME_ID_LENGTH = 255;

    private final int minPlayers;
    private final int maxPlayers;

    /**
     * Creates a validator configured with the supported player-count bounds.
     *
     * @param minPlayers minimum allowed players per match
     * @param maxPlayers maximum allowed players per match
     */
    RankingInputValidator(int minPlayers, int maxPlayers) {
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
    }

    /**
     * Trims and validates a nickname.
     *
     * @param nickname nickname to normalize
     * @return trimmed nickname
     */
    String normalizeNickname(String nickname) {
        if (nickname == null) {
            throw new IllegalArgumentException("Nickname cannot be null");
        }

        String normalized = nickname.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Nickname cannot be blank");
        }

        if (normalized.length() > MAX_NICKNAME_LENGTH) {
            throw new IllegalArgumentException("Nickname cannot be longer than 255 characters");
        }

        return normalized;
    }

    /**
     * Trims and validates a game id.
     *
     * @param gameId game id to normalize
     * @return trimmed game id
     */
    String normalizeGameId(String gameId) {
        if (gameId == null) {
            throw new IllegalArgumentException("Game id cannot be null");
        }

        String normalized = gameId.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Game id cannot be blank");
        }

        if (normalized.length() > MAX_GAME_ID_LENGTH) {
            throw new IllegalArgumentException("Game id cannot be longer than 255 characters");
        }

        return normalized;
    }

    /**
     * Validates that match timestamp is provided.
     *
     * @param playedAtUtc match timestamp
     * @return same timestamp if valid
     */
    Instant normalizePlayedAt(Instant playedAtUtc) {
        if (playedAtUtc == null) {
            throw new IllegalArgumentException("Played at timestamp cannot be null");
        }

        return playedAtUtc;
    }

    /**
     * Validates player count against configured boundaries.
     *
     * @param playerCount number of players
     * @return same value if valid
     */
    int validatePlayerCount(int playerCount) {
        if (playerCount < this.minPlayers || playerCount > this.maxPlayers) {
            throw new IllegalArgumentException("Player count must be between 2 and 5");
        }

        return playerCount;
    }

    /**
     * Validates and normalizes match scores while preserving order.
     *
     * @param finalScores final scores as received from the caller
     * @param playerCount expected number of rows
     * @return immutable list of normalized scores
     */
    List<MatchScore> normalizeScores(List<MatchScore> finalScores, int playerCount) {
        if (finalScores == null || finalScores.isEmpty()) {
            throw new IllegalArgumentException("Final scores cannot be null or empty");
        }

        if (finalScores.size() != playerCount) {
            throw new IllegalArgumentException("Final scores size must match player count");
        }

        Set<String> nicknames = new HashSet<>();
        List<MatchScore> normalizedScores = new ArrayList<>();

        for (MatchScore score : finalScores) {
            if (score == null) {
                throw new IllegalArgumentException("Final scores cannot contain null elements");
            }

            String normalizedNickname = this.normalizeNickname(score.nickname());

            if (!nicknames.add(normalizedNickname)) {
                throw new IllegalArgumentException("Final scores cannot contain duplicate nicknames");
            }

            normalizedScores.add(new MatchScore(normalizedNickname, score.finalScore()));
        }

        return List.copyOf(normalizedScores);
    }
}
