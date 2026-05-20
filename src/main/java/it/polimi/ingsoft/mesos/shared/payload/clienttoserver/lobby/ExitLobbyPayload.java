package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.lobby;

import java.io.Serializable;
/**
 * Payload sent by a player to request exiting their current lobby.
 * <p>
 * If the exiting player is the host of the lobby, the host authority
 * is automatically delegated to another active player.
 */
public class ExitLobbyPayload implements Serializable, LobbyClientPayload {
}
