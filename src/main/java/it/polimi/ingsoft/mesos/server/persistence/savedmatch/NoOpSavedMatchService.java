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

    private NoOpSavedMatchService() {
    }

    @Override
    public boolean isPersistenceEnabled() {
        return false;
    }

    @Override
    public void initializeMatch(Game game, String hostNickname) {
    }

    @Override
    public void saveTurnStarted(Game game, String hostNickname) {
    }

    @Override
    public void saveEraChanged(Game game, String hostNickname) {
    }

    @Override
    public void saveTurnFinished(Game game, String hostNickname) {
    }

    @Override
    public void completeMatch(Game game, String hostNickname) {
    }

    @Override
    public void saveMatch(Game game, String hostNickname, SavedMatchCheckpointType checkpointType) {
    }

    @Override
    public Game restoreGame(String gameId) {
        throw new IllegalStateException("Saved-match restore is not available in no-db mode");
    }

    @Override
    public void markPlayerOnline(String gameId, String nickname, String sessionToken) {
    }

    @Override
    public void markPlayerOffline(String gameId, String nickname) {
    }

    @Override
    public int deleteExpiredInProgressMatches() {
        return 0;
    }
}
