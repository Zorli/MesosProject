package it.polimi.ingsoft.mesos.shared.payload.servertoclient.game;

import java.io.Serializable;
import java.util.List;

/**
 * All-time ranking of finished matches, filtered to a single player count.
 *
 * <p>The server broadcasts this payload right after a match ends and its results
 * have been persisted, so the included rows already account for the match that
 * just finished. The ranking lists, for every completed game played with the same
 * number of players, the score obtained by each participant, ordered from the best
 * score to the worst (ties are broken by the earliest date).
 *
 * <p>Rows produced by the match that just finished are flagged through
 * {@link RankingRow#currentGame}. This lets each client highlight its own placement
 * ("you placed #X") without the server having to tailor the payload per recipient:
 * the client simply looks for the flagged row whose nickname matches its own.
 */
public class RankingPayload implements Serializable, GameServerPayload {

    /** Number of players of the matches this ranking refers to. */
    public final int numPlayers;

    /** Ranking rows ordered by rank ascending (rank {@code 1} is the best score). */
    public final List<RankingRow> rows;

    /**
     * Constructs a new RankingPayload.
     *
     * @param numPlayers number of players the ranking is filtered by
     * @param rows ranking rows ordered by rank ascending
     */
    public RankingPayload(int numPlayers, List<RankingRow> rows) {
        this.numPlayers = numPlayers;
        this.rows = rows;
    }

    /**
     * A single entry of the all-time ranking: one stored game result.
     *
     * <p>The same nickname can appear in several rows because every completed game
     * contributes one result per player.
     */
    public static final class RankingRow implements Serializable {

        /** One-based position of this result in the ranking for this player count. */
        public final int rank;

        /** Nickname of the player who achieved this score. */
        public final String nickname;

        /** Prestige points scored by the player in that match (the final score). */
        public final int score;

        /** ISO-8601 date/time the match was played (e.g. {@code 2026-06-14T20:31:05.123}). */
        public final String datePlayed;

        /** {@code true} when this row was produced by the match that just finished. */
        public final boolean currentGame;

        /**
         * Constructs a new ranking row.
         *
         * @param rank one-based position in the ranking
         * @param nickname nickname of the player
         * @param score prestige points scored
         * @param datePlayed ISO-8601 date/time the match was played
         * @param currentGame whether the row belongs to the match that just finished
         */
        public RankingRow(int rank, String nickname, int score, String datePlayed, boolean currentGame) {
            this.rank = rank;
            this.nickname = nickname;
            this.score = score;
            this.datePlayed = datePlayed;
            this.currentGame = currentGame;
        }
    }
}
