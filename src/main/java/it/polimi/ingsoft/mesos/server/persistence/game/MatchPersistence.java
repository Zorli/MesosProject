package it.polimi.ingsoft.mesos.server.persistence.game;

import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;

import java.util.List;

/**
 * Abstraction over checkpoint persistence for active matches.
 */
public interface MatchPersistence {
    /**
     * Loads every available persisted active match.
     *
     * @return list of snapshots, one per restored game id
     */
    List<SavedMatchSnapshot> loadActiveMatches();

    /**
     * Persists the provided match snapshot.
     *
     * @param snapshot snapshot to persist
     */
    void save(SavedMatchSnapshot snapshot);

    /**
     * Deletes checkpoint for the provided game id.
     *
     * @param gameId target game identifier
     */
    void delete(int gameId);

    /**
     * Returns a no-op implementation that never reads or writes to disk.
     */
    static MatchPersistence disabled() {
        return NoOpMatchPersistence.INSTANCE;
    }

    final class NoOpMatchPersistence implements MatchPersistence {
        static final MatchPersistence INSTANCE = new NoOpMatchPersistence();

        @Override
        public List<SavedMatchSnapshot> loadActiveMatches() {
            return List.of();
        }

        @Override
        public void save(SavedMatchSnapshot snapshot) {
        }

        @Override
        public void delete(int gameId) {
        }
    }
}
