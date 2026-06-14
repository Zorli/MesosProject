package it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby;

import java.io.Serializable;
import java.util.List;

/**
 * Payload sent by the server to notify players in a lobby of any state changes.
 * <p>
 * This contains the event type, target nicknames, message alerts, updated member list,
 * lobby capacity, and current host details.
 */
public class LobbyUpdatePayload implements Serializable, LobbyServerPayload {
    /** The classification of the lobby event. */
    public final LobbyEventType event;

    /** The nickname of the player affected by or initiating the event. */
    public final String nickname;

    /** An optional descriptive text/alert message. */
    public final String message;

    /** The unique identifier of the lobby room. */
    public final Integer lobbyId;

    /** The updated list of nicknames of all players currently inside the lobby. */
    public final List<String> players;

    /** The nickname of the current lobby host. */
    public final String hostNickname;

    /** The maximum player limit allowed for this lobby. */
    public final Integer maxPlayers;

    /**
     * Constructs a new LobbyUpdatePayload.
     *
     * @param event the type of the lobby event
     * @param nickname the player nickname involved
     * @param message descriptive text message
     * @param lobbyId the identifier of the lobby
     * @param players the list of current lobby members
     * @param hostNickname the nickname of the current host
     * @param maxPlayers the maximum capacity of the lobby
     */
    public LobbyUpdatePayload(
            LobbyEventType event,
            String nickname,
            String message,
            Integer lobbyId,
            List<String> players,
            String hostNickname,
            Integer maxPlayers
    ) {
        this.event = event;
        this.nickname = nickname;
        this.message = message;
        this.lobbyId = lobbyId;
        this.players = players;
        this.hostNickname = hostNickname;
        this.maxPlayers = maxPlayers;
    }
}
