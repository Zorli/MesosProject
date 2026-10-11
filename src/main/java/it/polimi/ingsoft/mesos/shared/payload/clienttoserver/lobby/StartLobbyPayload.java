package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.lobby;

import java.io.Serializable;
/**
 * Payload sent by the host player to trigger starting the game.
 * <p>
 * This starts the lobby countdown timer, transitioning the lobby
 * to a starting state, and eventually creating and launching the game.
 */
public class StartLobbyPayload implements Serializable, LobbyClientPayload {
}
