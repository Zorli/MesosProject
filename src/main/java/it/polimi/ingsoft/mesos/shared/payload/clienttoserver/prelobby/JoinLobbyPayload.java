package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby;

import java.io.Serializable;
/**
 * Payload sent by an authenticated client to request joining a specific lobby.
 * <p>
 * If the lobby has space and is in a joinable phase, the requester
 * will be added to the lobby's player list.
 */
public class JoinLobbyPayload implements Serializable, PreLobbyClientPayload {
    /** The identifier of the lobby room to join. */
    public final int lobbyId;

    /**
     * Constructs a new JoinLobbyPayload.
     *
     * @param lobbyId the target lobby identifier
     */
    public JoinLobbyPayload(int lobbyId) {
        this.lobbyId = lobbyId;
    }
}
