package it.polimi.ingsoft.mesos.server.persistence.game.restore;

import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.List;

/**
 * Immutable container produced by snapshot restoration.
 */
public record RestoredMatch(
        Game game,
        List<Player> players,
        String hostNickname
) {
}
