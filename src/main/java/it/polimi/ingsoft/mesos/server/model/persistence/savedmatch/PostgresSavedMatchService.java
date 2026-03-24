package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.controller.ranking.PostgresConnectionConfig;

import java.util.Objects;

/**
 * Application service responsible for saving, restoring and tracking resumable matches.
 */
public final class PostgresSavedMatchService implements SavedMatchService {
    private final SavedMatchRepository repository;

    /**
     * Creates the service with PostgreSQL connection settings and retry limit.
     *
     * @param connectionConfig PostgreSQL connection settings
     * @param maxBusyRetries maximum retries for transient PostgreSQL failures
     */
    public PostgresSavedMatchService(PostgresConnectionConfig connectionConfig, int maxBusyRetries) {
        this(new PostgresSavedMatchRepository(connectionConfig, maxBusyRetries));
    }

    /**
     * Creates the service with an already prepared repository.
     *
     * @param repository repository to use
     */
    public PostgresSavedMatchService(SavedMatchRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
    }

    /**
     * Returns whether this service is backed by a real persistence implementation.
     *
     * @return always {@code true}
     */
    @Override
    public boolean isPersistenceEnabled() {
        return true;
    }

    /**
     * Persists the initial match state right after setup.
     *
     * @param game game to persist
     * @param hostNickname original host nickname
     */
    @Override
    public void initializeMatch(Game game, String hostNickname) {
        this.saveMatch(game, hostNickname, SavedMatchCheckpointType.GAME_INITIALIZED);
    }

    /**
     * Persists the match state when a turn starts.
     *
     * @param game game to persist
     * @param hostNickname original host nickname
     */
    @Override
    public void saveTurnStarted(Game game, String hostNickname) {
        this.saveMatch(game, hostNickname, SavedMatchCheckpointType.TURN_STARTED);
    }

    /**
     * Persists the match state when the era changes.
     *
     * @param game game to persist
     * @param hostNickname original host nickname
     */
    @Override
    public void saveEraChanged(Game game, String hostNickname) {
        this.saveMatch(game, hostNickname, SavedMatchCheckpointType.ERA_CHANGED);
    }

    /**
     * Persists the match state when a turn finishes.
     *
     * @param game game to persist
     * @param hostNickname original host nickname
     */
    @Override
    public void saveTurnFinished(Game game, String hostNickname) {
        this.saveMatch(game, hostNickname, SavedMatchCheckpointType.TURN_FINISHED);
    }

    /**
     * Persists the final completed-match state.
     *
     * @param game game to persist
     * @param hostNickname original host nickname
     */
    @Override
    public void completeMatch(Game game, String hostNickname) {
        this.saveMatch(game, hostNickname, SavedMatchCheckpointType.MATCH_COMPLETED);
    }

    /**
     * Saves the current game state at the specified checkpoint.
     *
     * @param game game to persist
     * @param hostNickname original host nickname
     * @param checkpointType checkpoint producing this save
     */
    @Override
    public void saveMatch(Game game, String hostNickname, SavedMatchCheckpointType checkpointType) {
        SavedMatchAggregate aggregate = SavedMatchMapper.fromGame(game, hostNickname, checkpointType);
        this.repository.save(aggregate);
    }

    /**
     * Restores one saved match from PostgreSQL together with its original ownership metadata.
     *
     * @param gameId identifier of the match to restore
     * @return restored match aggregate plus original host metadata
     */
    @Override
    public SavedMatchRestoreResult restoreMatch(String gameId) {
        SavedMatchAggregate aggregate = this.repository.load(gameId);
        return new SavedMatchRestoreResult(
                SavedMatchRestoreFactory.restore(aggregate),
                aggregate.hostNickname()
        );
    }

    /**
     * Marks the given player as online inside the specified saved match.
     *
     * @param gameId match identifier
     * @param nickname player nickname
     * @param sessionToken unique session token
     */
    @Override
    public void markPlayerOnline(String gameId, String nickname, String sessionToken) {
        this.repository.markPlayerOnline(gameId, nickname, sessionToken);
    }

    /**
     * Marks the given player as offline inside the specified saved match.
     *
     * @param gameId match identifier
     * @param nickname player nickname
     */
    @Override
    public void markPlayerOffline(String gameId, String nickname) {
        this.repository.markPlayerOffline(gameId, nickname);
    }

    /**
     * Deletes matches older than the retention window.
     *
     * @return number of deleted matches
     */
    @Override
    public int deleteExpiredInProgressMatches() {
        return this.repository.deleteExpiredInProgressMatches();
    }
}
