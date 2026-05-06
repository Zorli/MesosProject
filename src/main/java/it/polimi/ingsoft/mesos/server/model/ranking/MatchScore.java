package it.polimi.ingsoft.mesos.server.model.ranking;

/**
 * Immutable final score of a player inside a match.
 *
 * @param nickname unique player nickname
 * @param finalScore signed match score used for ordering placements
 */
public record MatchScore(String nickname, int finalScore) {

    /**
     * Validates score payload.
     */
    public MatchScore {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("Nickname cannot be null or blank");
        }
    }
}
