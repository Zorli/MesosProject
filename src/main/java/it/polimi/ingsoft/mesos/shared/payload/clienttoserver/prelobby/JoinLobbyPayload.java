package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby;

import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;

import java.io.Serializable;

/**
 * Payload sent by an authenticated client to request joining a specific lobby.
 * <p>
 * If the lobby has space and is in a joinable phase, the requester
 * will be added to the lobby's player list with the selected totem color.
 */
public class JoinLobbyPayload implements Serializable, PreLobbyClientPayload {
    /** The identifier of the lobby room to join. */
    public final int lobbyId;
    /** The totem color selected by the joining player. */
    public final TotemColor totemColor;

    /**
     * Constructs a new JoinLobbyPayload.
     *
     * @param lobbyId the target lobby identifier
     * @param totemColor the totem color selected by the joining player; the server model rejects null values
     */
    public JoinLobbyPayload(int lobbyId, TotemColor totemColor) {
        this.lobbyId = lobbyId;
        this.totemColor = totemColor;
    }
}
