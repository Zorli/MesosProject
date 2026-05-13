package it.polimi.ingsoft.mesos.server.model.persistence;

import it.polimi.ingsoft.mesos.server.persistence.game.MatchPersistence;

/**
 * Runtime service container for the server application.
 */
public record ServerServices(MatchPersistence matchPersistence) {
}
