package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyEventType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;

import java.util.List;
import java.util.Map;

/**
 * Mapper responsible for converting internal domain models (like {@link Lobby}) or action results
 * into serialization-ready network payloads to be sent to clients.
 *
 * <p>This decoupling ensures that the network layer and raw payload format are separated from
 * the core domain models.
 */
public final class LobbyPayloadMapper {

    /**
     * Creates an update payload notifying that a player has successfully joined the lobby.
     *
     * @param nickname the nickname of the joining player
     * @param lobby the {@link Lobby} instance that was joined
     * @return the player join update payload
     */
    public LobbyUpdatePayload lobbyJoined(String nickname, Lobby lobby) {
        return this.fromLobby(LobbyEventType.LOBBY_JOINED, nickname, "Player " +nickname+ " joined the Lobby successfully", lobby);
    }

    /**
     * Creates an update payload notifying that a player has left the lobby or that the lobby has been closed.
     *
     * @param nickname the nickname of the leaving player
     * @param lobby the {@link Lobby} instance being left, or {@code null} if the lobby was closed/disposed
     * @param lobbyId the identifier of the lobby
     * @return the player exit or lobby closure update payload
     */
    public LobbyUpdatePayload lobbyLeft(String nickname, Lobby lobby, int lobbyId) {
        if (lobby == null) {
            return new LobbyUpdatePayload(
                    LobbyEventType.LOBBY_LEFT,
                    nickname,
                    "Lobby closed",
                    lobbyId,
                    List.of(),
                    null,
                    null,
                    Map.of()
            );
        }

        return this.fromLobby(LobbyEventType.LOBBY_LEFT, nickname, "Player "+nickname+" left the lobby successfully", lobby);
    }

    public LobbyUpdatePayload lobbyLeftConfirmation(String nickname, int lobbyId) {
        return new LobbyUpdatePayload(
                LobbyEventType.LOBBY_LEFT,
                nickname,
                "Lobby left successfully",
                lobbyId,
                List.of(),
                null,
                null,
                Map.of()
        );
    }

    /**
     * Creates an update payload notifying that the match in the lobby has started.
     *
     * @param nickname the host player nickname who started the match
     * @param lobby the {@link Lobby} instance which transition is starting
     * @return the match started update payload
     */
    public LobbyUpdatePayload lobbyStarted(String nickname, Lobby lobby) {
        return this.fromLobby(LobbyEventType.LOBBY_STARTED, nickname, "Match has started", lobby);
    }

    /**
     * Utility builder that extracts fields from a {@link Lobby} instance and wraps them into a standard {@link LobbyUpdatePayload}.
     */
    private LobbyUpdatePayload fromLobby(
            LobbyEventType event,
            String nickname,
            String message,
            Lobby lobby
    ) {
        return new LobbyUpdatePayload(
                event,
                nickname,
                message,
                lobby.getId(),
                lobby.getPlayers(),
                lobby.getHostNickname(),
                lobby.getChosenCapacity(),
                lobby.getPlayerTotemColors()
        );
    }
}
