package it.polimi.ingsoft.mesos.server.model.ranking;

/**
 * Snapshot of a player's rating state before processing a match.
 *
 * @param nickname player nickname
 * @param elo current Elo before update
 * @param gamesPlayed total games played before update
 * @param wins total wins before update
 * @param winStreak current consecutive wins before update
 */
record RatingSnapshot(String nickname, int elo, int gamesPlayed, int wins, int winStreak) {
}
