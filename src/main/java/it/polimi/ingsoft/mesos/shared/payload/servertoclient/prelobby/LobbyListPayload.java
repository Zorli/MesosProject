package it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.ListLobbiesPayload;

import java.io.Serializable;
import java.util.List;

/**
 * Payload sent by the server containing the list of all open lobbies.
 * <p>
 * This is sent as a reply to a {@link ListLobbiesPayload} request.
 */
public class LobbyListPayload implements Serializable, PreLobbyServerPayload {
    /** The list of simplified lobby summaries currently available. */
    public final List<LobbySummaryPayload> lobbies;

    /**
     * Constructs a new LobbyListPayload.
     *
     * @param lobbies the list of lobby summary structures
     */
    public LobbyListPayload(List<LobbySummaryPayload> lobbies) {
        this.lobbies = lobbies;
    }
}
