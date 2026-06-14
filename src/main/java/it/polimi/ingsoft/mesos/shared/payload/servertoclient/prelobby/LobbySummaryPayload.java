package it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby;
import java.io.Serializable;
/**
 * Represents a simplified snapshot/summary of a single lobby room on the server.
 * <p>
 * This is used to display the list of joinable rooms to players in the pre-lobby.
 */
public class LobbySummaryPayload  implements Serializable {
    /** The unique identifier of the lobby room. */
    public final int lobbyId;

    /** The nickname of the player who currently hosts the lobby. */
    public final String hostNickname;

    /** The current number of players present inside the lobby. */
    public final int currentPlayers;

    /** The maximum capacity/player limit allowed for this lobby. */
    public final int maxPlayers;

    /** The current status of the lobby, as the name of a {@code LobbyStatus} (WAITING, READY, STARTED, or CLOSED). */
    public final String status;

    /**
     * Constructs a new LobbySummaryPayload with the specified lobby parameters.
     *
     * @param lobbyId the identifier of the lobby
     * @param hostNickname the nickname of the lobby host
     * @param currentPlayers the current number of players in the lobby
     * @param maxPlayers the maximum capacity of the lobby
     * @param status the current status string of the lobby
     */
    public LobbySummaryPayload(int lobbyId, String hostNickname, int currentPlayers, int maxPlayers, String status) {
        this.lobbyId = lobbyId;
        this.hostNickname = hostNickname;
        this.currentPlayers = currentPlayers;
        this.maxPlayers = maxPlayers;
        this.status = status;
    }
}
