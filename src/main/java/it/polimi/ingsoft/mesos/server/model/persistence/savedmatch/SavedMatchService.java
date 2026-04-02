package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.game.Game;

/**
 * Application-service contract for match persistence, restore and player presence tracking.
 */
public interface SavedMatchService {
    /**
     * Returns whether this implementation has a real persistence backend enabled.
     *
     * @return {@code true} when persistence is active
     */
    boolean isPersistenceEnabled();

    /**
     * Persists the initial match state right after setup.
     *
     * @param game game to persist
     * @param hostNickname original host nickname
     */
    void initializeMatch(Game game, String hostNickname);

    /**
     * Persists the match state when a turn starts.
     *
     * @param game game to persist
     * @param hostNickname original host nickname
     */
    void saveTurnStarted(Game game, String hostNickname);

    /**
     * Persists the match state when the era changes.
     *
     * @param game game to persist
     * @param hostNickname original host nickname
     */
    void saveEraChanged(Game game, String hostNickname);

    /**
     * Persists the match state when a turn finishes.
     *
     * @param game game to persist
     * @param hostNickname original host nickname
     */
    void saveTurnFinished(Game game, String hostNickname);

    /**
     * Persists the final-scoring boundary snapshot.
     *
     * <p>This checkpoint is reused for both snapshots that are still in {@code FINAL_SCORING} and
     * snapshots already marked as finished after final score calculation.
     *
     * @param game game to persist
     * @param hostNickname original host nickname
     */
    void completeMatch(Game game, String hostNickname);

    /**
     * Persists the current game state at an explicit checkpoint.
     *
     * @param game game to persist
     * @param hostNickname original host nickname
     * @param checkpointType checkpoint producing this save
     */
    void saveMatch(Game game, String hostNickname, SavedMatchCheckpointType checkpointType);

    /**
     * Restores one saved match from persistence, including ownership metadata needed by the application layer.
     *
     * @param gameId identifier of the match to restore
     * @return restored match aggregate plus original host metadata
     */
    SavedMatchRestoreResult restoreMatch(String gameId);

    /**
     * Restores one game aggregate from persistence.
     *
     * @param gameId identifier of the match to restore
     * @return restored game aggregate
     */
    default Game restoreGame(String gameId) {
        return this.restoreMatch(gameId).game();
    }

    /**
     * Marks one player as online for the given match.
     *
     * @param gameId match identifier
     * @param nickname player nickname
     * @param sessionToken unique session token
     */
    void markPlayerOnline(String gameId, String nickname, String sessionToken);

    /**
     * Marks one player as offline for the given match.
     *
     * @param gameId match identifier
     * @param nickname player nickname
     */
    void markPlayerOffline(String gameId, String nickname);

    /**
     * Deletes expired in-progress matches from persistence.
     *
     * @return number of deleted matches
     */
    int deleteExpiredInProgressMatches();
}
