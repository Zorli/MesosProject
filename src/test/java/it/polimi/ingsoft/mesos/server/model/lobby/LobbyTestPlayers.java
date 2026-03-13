package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;

final class LobbyTestPlayers {

    private LobbyTestPlayers() {
    }

    static Player playerWithNickname(String nickname) {
        return new Player(nickname, 0, 0, TotemColor.RED, null);
    }
}
