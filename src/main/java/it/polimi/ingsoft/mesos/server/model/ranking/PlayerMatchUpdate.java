package it.polimi.ingsoft.mesos.server.model.ranking;

/**
 * Computed per-player update produced while processing a match.
 *
 * @param nickname player nickname
 * @param finalScore final score used for placement
 * @param placement final placement (1-based)
 * @param eloBefore Elo before update
 * @param eloDelta Elo variation from this match
 * @param eloAfter Elo after update
 * @param gamesAfter games played after update
 * @param winsAfter wins after update
 * @param winStreakAfter win streak after update
 */
record PlayerMatchUpdate(String nickname,
                         int finalScore,
                         int placement,
                         int eloBefore,
                         int eloDelta,
                         int eloAfter,
                         int gamesAfter,
                         int winsAfter,
                         int winStreakAfter) {
}
