package it.polimi.ingsoft.mesos.server.persistence;

import java.util.List;

/**
 * Plain data result returned by {@link GameResultRepository} after a match is
 * recorded: the all-time ranking, filtered to one player count, read straight
 * from the database.
 *
 * <p>This is a persistence-layer DTO, intentionally decoupled from the network
 * payloads: the conversion to a {@code RankingPayload} is done by the model mapper,
 * so the repository never depends on the wire format.
 */
public record RankingResult(int numPlayers, List<RankingEntry> entries) {

    /**
     * One row of the ranking: a single stored game result.
     *
     * @param rank one-based position in the ranking (1 = best score)
     * @param nickname nickname of the player who achieved this score
     * @param score prestige points scored in that match
     * @param datePlayed ISO-8601 date/time the match was played
     * @param currentGame whether this row belongs to the match that just finished
     */
    public record RankingEntry(int rank, String nickname, int score, String datePlayed, boolean currentGame) {
    }
}
