package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;

/**
 * Small factory of lobby test players used to keep lobby-focused tests concise.
 */
final class LobbyTestPlayers {
    /**
     * Prevents instantiation of the test helper class.
     */
    private LobbyTestPlayers() {
    }

    /**
     * Creates a player with default zeroed resources and a fixed totem color for lobby tests.
     *
     * @param nickname nickname assigned to the generated player
     * @return player ready to be used in lobby test scenarios
     */
    static Player playerWithNickname(String nickname) {
        return new Player(nickname, 0, 0, TotemColor.RED, null);
    }
}
