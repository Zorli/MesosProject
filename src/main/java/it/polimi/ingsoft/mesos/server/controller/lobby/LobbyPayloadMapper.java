package it.polimi.ingsoft.mesos.server.controller.lobby;

import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbySummaryPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyEventType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;

import java.util.List;

/**
 * Mapper responsible for converting internal domain models (like {@link Lobby}) or action results
 * into serialization-ready network payloads to be sent to clients.
 *
 * <p>This decoupling ensures that the network layer and raw payload format are separated from
 * the core domain models.
 */
public final class LobbyPayloadMapper {

    /**
     * Creates an update payload signifying a successful authentication of a client nickname.
     *
     * @param nickname the nickname of the authenticated player
     * @return the payload containing the authentication confirmation
     */
    public LobbyUpdatePayload authenticated(String nickname) {
        return new LobbyUpdatePayload(
                LobbyEventType.AUTHENTICATED,
                nickname,
                "Authentication completed successfully",
                null,
                null,
                null,
                null
        );
    }

    /**
     * Creates an update payload signifying that a lobby has been successfully created.
     *
     * @param nickname the host player nickname who created the lobby
     * @param lobby the created {@link Lobby} instance
     * @return the lobby creation update payload
     */
    public LobbyUpdatePayload lobbyCreated(String nickname, Lobby lobby) {
        return this.fromLobby(LobbyEventType.LOBBY_CREATED, nickname, "Lobby created successfully", lobby);
    }

    /**
     * Creates an update payload notifying that a player has successfully joined the lobby.
     *
     * @param nickname the nickname of the joining player
     * @param lobby the {@link Lobby} instance that was joined
     * @return the player join update payload
     */
    public LobbyUpdatePayload lobbyJoined(String nickname, Lobby lobby) {
        return this.fromLobby(LobbyEventType.LOBBY_JOINED, nickname, "Lobby joined successfully", lobby);
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
                    null
            );
        }

        return this.fromLobby(LobbyEventType.LOBBY_LEFT, nickname, "Lobby left successfully", lobby);
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
     * Formats a list of active {@link Lobby} instances into a {@link LobbyListPayload} to send
     * to clients looking for available lobbies to join.
     *
     * @param lobbies the list of active lobbies
     * @return the formatted lobby list payload containing summaries of all lobbies
     */
    public LobbyListPayload lobbyList(List<Lobby> lobbies) {
        return new LobbyListPayload(lobbies.stream()
                .map(this::summary)
                .toList());
    }

    /**
     * Formats a single {@link Lobby} instance into a lightweight, serializable {@link LobbySummaryPayload}.
     *
     * @param lobby the lobby model to summarize
     * @return the serializable lobby summary
     */
    public LobbySummaryPayload summary(Lobby lobby) {
        return new LobbySummaryPayload(
                lobby.getId(),
                lobby.getHostNickname(),
                lobby.getPlayers().size(),
                lobby.getChosenCapacity(),
                lobby.getStatus().name()
        );
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
                lobby.getChosenCapacity()
        );
    }
}
