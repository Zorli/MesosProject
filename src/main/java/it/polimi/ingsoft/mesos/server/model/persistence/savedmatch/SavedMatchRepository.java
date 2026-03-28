package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

/**
 * Contract for loading and storing resumable matches.
 */
public interface SavedMatchRepository {
    /**
     * Persists one normalized match aggregate atomically.
     *
     * @param aggregate saved match aggregate to store
     */
    void save(SavedMatchAggregate aggregate);

    /**
     * Loads one normalized match aggregate.
     *
     * @param gameId identifier of the match to load
     * @return loaded match aggregate
     */
    SavedMatchAggregate load(String gameId);

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
     * Deletes in-progress matches that were not updated within the retention window.
     *
     * @return number of deleted matches
     */
    int deleteExpiredInProgressMatches();
}
