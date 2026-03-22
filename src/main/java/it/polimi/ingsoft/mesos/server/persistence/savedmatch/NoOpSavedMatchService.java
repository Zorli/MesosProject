package it.polimi.ingsoft.mesos.server.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.GameState.Game;

/**
 * Naive no-database implementation used when match persistence is explicitly disabled.
 */
public final class NoOpSavedMatchService implements SavedMatchService {
    /**
     * Shared singleton instance.
     */
    public static final NoOpSavedMatchService INSTANCE = new NoOpSavedMatchService();

    /**
     * Prevents external instantiation of the singleton service.
     */
    private NoOpSavedMatchService() {
    }

    /**
     * Returns whether a real saved-match backend is active.
     *
     * @return always {@code false} in no-db mode
     */
    @Override
    public boolean isPersistenceEnabled() {
        return false;
    }

    /**
     * Ignores initial-match persistence requests because saved-match persistence is disabled.
     *
     * @param game game that would otherwise be persisted
     * @param hostNickname original host nickname
     */
    @Override
    public void initializeMatch(Game game, String hostNickname) {
    }

    /**
     * Ignores turn-start checkpoints because saved-match persistence is disabled.
     *
     * @param game game that would otherwise be persisted
     * @param hostNickname original host nickname
     */
    @Override
    public void saveTurnStarted(Game game, String hostNickname) {
    }

    /**
     * Ignores era-change checkpoints because saved-match persistence is disabled.
     *
     * @param game game that would otherwise be persisted
     * @param hostNickname original host nickname
     */
    @Override
    public void saveEraChanged(Game game, String hostNickname) {
    }

    /**
     * Ignores turn-finished checkpoints because saved-match persistence is disabled.
     *
     * @param game game that would otherwise be persisted
     * @param hostNickname original host nickname
     */
    @Override
    public void saveTurnFinished(Game game, String hostNickname) {
    }

    /**
     * Ignores match-completion checkpoints because saved-match persistence is disabled.
     *
     * @param game game that would otherwise be persisted
     * @param hostNickname original host nickname
     */
    @Override
    public void completeMatch(Game game, String hostNickname) {
    }

    /**
     * Ignores generic save requests because saved-match persistence is disabled.
     *
     * @param game game that would otherwise be persisted
     * @param hostNickname original host nickname
     * @param checkpointType ignored checkpoint type
     */
    @Override
    public void saveMatch(Game game, String hostNickname, SavedMatchCheckpointType checkpointType) {
    }

    /**
     * Fails because no persistent backend exists from which a match could be restored.
     *
     * @param gameId identifier of the requested match
     * @return never returns normally
     */
    @Override
    public SavedMatchRestoreResult restoreMatch(String gameId) {
        throw new IllegalStateException("Saved-match restore is not available in no-db mode");
    }

    /**
     * Ignores online-presence updates because saved-match persistence is disabled.
     *
     * @param gameId match identifier
     * @param nickname player nickname
     * @param sessionToken connection token
     */
    @Override
    public void markPlayerOnline(String gameId, String nickname, String sessionToken) {
    }

    /**
     * Ignores offline-presence updates because saved-match persistence is disabled.
     *
     * @param gameId match identifier
     * @param nickname player nickname
     */
    @Override
    public void markPlayerOffline(String gameId, String nickname) {
    }

    /**
     * Reports that no expired matches were deleted because nothing is persisted in no-db mode.
     *
     * @return always {@code 0}
     */
    @Override
    public int deleteExpiredInProgressMatches() {
        return 0;
    }
}
