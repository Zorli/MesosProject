package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby;

import java.io.Serializable;
/**
 * Payload sent by an authenticated client to request the list of all open lobbies.
 * <p>
 * The server responds with a list containing basic summaries of active lobbies
 * that can currently be joined.
 */
public class ListLobbiesPayload implements Serializable, PreLobbyClientPayload {
    /**
     * Constructs a new ListLobbiesPayload.
     */
    public ListLobbiesPayload() {
    }
}
