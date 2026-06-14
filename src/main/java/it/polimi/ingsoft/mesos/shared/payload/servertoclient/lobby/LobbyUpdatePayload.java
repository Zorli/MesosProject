package it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby;

import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Payload sent by the server to notify players in a lobby of any state changes.
 * <p>
 * This contains the event type, target nickname, message alerts, updated member list,
 * lobby capacity, current host details, and the totem color chosen by each player
 * currently seated in the lobby.
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

    /** Current association between lobby player nicknames and their selected totem colors. */
    public final Map<String, TotemColor> playerTotemColors;

    /**
     * Constructs a new LobbyUpdatePayload including the player-to-totem association.
     *
     * <p>When the update is a direct confirmation to the player who left the lobby, the lobby
     * state may be intentionally empty. In that case a null color map is normalized to an empty map.
     *
     * @param event the type of the lobby event
     * @param nickname the player nickname involved
     * @param message descriptive text message
     * @param lobbyId the identifier of the lobby
     * @param players the list of current lobby members
     * @param hostNickname the nickname of the current host
     * @param maxPlayers the maximum capacity of the lobby
     * @param playerTotemColors map from player nickname to selected totem color
     */
    public LobbyUpdatePayload(
            LobbyEventType event,
            String nickname,
            String message,
            Integer lobbyId,
            List<String> players,
            String hostNickname,
            Integer maxPlayers,
            Map<String, TotemColor> playerTotemColors
    ) {
        this.event = event;
        this.nickname = nickname;
        this.message = message;
        this.lobbyId = lobbyId;
        this.players = players;
        this.hostNickname = hostNickname;
        this.maxPlayers = maxPlayers;
        this.playerTotemColors = playerTotemColors == null ? Map.of() : Map.copyOf(playerTotemColors);
    }
}
