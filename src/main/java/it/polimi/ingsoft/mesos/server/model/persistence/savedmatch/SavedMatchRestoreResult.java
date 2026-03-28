package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.GameState.Game;

/**
 * Result of one saved-match restore, including the rebuilt game aggregate and original ownership metadata.
 *
 * @param game restored game aggregate
 * @param hostNickname nickname of the player that created the original match
 */
public record SavedMatchRestoreResult(Game game, String hostNickname) {
}
