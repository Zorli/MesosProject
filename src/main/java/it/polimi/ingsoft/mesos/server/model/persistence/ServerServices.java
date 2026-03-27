package it.polimi.ingsoft.mesos.server.model.persistence;

import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchService;

/**
 * Runtime service container for the server application.
 *
 * @param rankingService ranking service selected for the current runtime mode
 * @param savedMatchService saved-match service selected for the current runtime mode
 */
public record ServerServices(RankingService rankingService, SavedMatchService savedMatchService) {
    /**
     * Validates service references.
     */
    public ServerServices {
        if (rankingService == null) {
            throw new IllegalArgumentException("Ranking service cannot be null");
        }
        if (savedMatchService == null) {
            throw new IllegalArgumentException("Saved-match service cannot be null");
        }
    }
}
