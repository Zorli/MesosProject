package it.polimi.ingsoft.mesos.server.model.persistence;

import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.server.persistence.game.MatchPersistence;

/**
 * Runtime service container for the server application.
 *
 * @param rankingService ranking service selected for the current runtime mode
 */
public record ServerServices(RankingService rankingService, MatchPersistence matchPersistence) {
}
