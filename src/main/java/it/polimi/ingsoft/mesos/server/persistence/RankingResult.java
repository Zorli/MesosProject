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
public class RankingResult {

    private final int numPlayers;
    private final List<RankingEntry> entries;

    /**
     * Constructs a new ranking result.
     *
     * @param numPlayers number of players of the matches this ranking refers to
     * @param entries ranking rows ordered by rank ascending
     */
    public RankingResult(int numPlayers, List<RankingEntry> entries) {
        this.numPlayers = numPlayers;
        this.entries = entries;
    }

    /**
     * Returns the number of players of the matches this ranking refers to.
     *
     * @return number of players used to filter the ranking
     */
    public int getNumPlayers() {
        return this.numPlayers;
    }

    /**
     * Returns the ranking rows ordered by rank ascending.
     *
     * @return ranking rows, where rank {@code 1} is the best score
     */
    public List<RankingEntry> getEntries() {
        return this.entries;
    }

    /**
     * One row of the ranking: a single stored game result.
     */
    public static class RankingEntry {

        private final int rank;
        private final String nickname;
        private final int score;
        private final String datePlayed;
        private final boolean currentGame;

        /**
         * Constructs a new ranking entry.
         *
         * @param rank one-based position in the ranking (1 = best score)
         * @param nickname nickname of the player who achieved this score
         * @param score prestige points scored in that match
         * @param datePlayed ISO-8601 date/time the match was played
         * @param currentGame whether this row belongs to the match that just finished
         */
        public RankingEntry(int rank, String nickname, int score, String datePlayed, boolean currentGame) {
            this.rank = rank;
            this.nickname = nickname;
            this.score = score;
            this.datePlayed = datePlayed;
            this.currentGame = currentGame;
        }

        /**
         * Returns the one-based position in the ranking.
         *
         * @return one-based rank, where {@code 1} is the best score
         */
        public int getRank() {
            return this.rank;
        }

        /**
         * Returns the nickname of the player who achieved this score.
         *
         * @return player nickname
         */
        public String getNickname() {
            return this.nickname;
        }

        /**
         * Returns the prestige points scored in the match.
         *
         * @return final score
         */
        public int getScore() {
            return this.score;
        }

        /**
         * Returns when the match was played.
         *
         * @return ISO-8601 date/time of the match
         */
        public String getDatePlayed() {
            return this.datePlayed;
        }

        /**
         * Tells whether this row belongs to the match that just finished.
         *
         * @return {@code true} if this row belongs to the current game
         */
        public boolean isCurrentGame() {
            return this.currentGame;
        }
    }
}
