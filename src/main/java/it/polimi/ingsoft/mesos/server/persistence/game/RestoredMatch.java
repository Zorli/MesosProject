package it.polimi.ingsoft.mesos.server.persistence.game;

import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.List;

public record RestoredMatch(Game game, List<Player> players, String hostNickname) {
}
