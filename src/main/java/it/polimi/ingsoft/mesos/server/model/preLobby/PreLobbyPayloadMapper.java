package it.polimi.ingsoft.mesos.server.model.preLobby;

import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyEventType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.AuthenticationResultPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbySummaryPayload;

import java.util.List;

/**
 * Data mapper class responsible for converting pre-lobby business objects and event
 * metadata into network-serializable payload objects.
 *
 * <p>This class acts as a translation layer between the server model's internal states
 * and the messages that are sent over the network to the clients. It separates serialization
 * concerns from domain and controller logic.
 */
public final class PreLobbyPayloadMapper {

    /**
     * Maps a successful user authentication event to an {@link AuthenticationResultPayload}.
     *
     * @param nickname the authenticated nickname of the user
     * @return the result payload indicating successful authentication
     */
    public AuthenticationResultPayload authenticated(String nickname) {
        return new AuthenticationResultPayload(nickname, "Authentication completed successfully");
    }

    /**
     * Maps the creation of a lobby to a {@link LobbyUpdatePayload} containing the initial lobby state.
     *
     * @param nickname the nickname of the lobby host/creator
     * @param lobby the newly created {@link Lobby} instance
     * @return the update payload notifying the creator and the system of the new lobby
     */
    public LobbyUpdatePayload lobbyCreated(String nickname, Lobby lobby) {
        return this.fromLobby(LobbyEventType.LOBBY_CREATED, nickname, "Lobby created successfully", lobby);
    }

    /**
     * Maps a list of active lobbies into a single {@link LobbyListPayload}.
     *
     * @param lobbies the list of active {@link Lobby} objects to be summarized and listed
     * @return the payload containing the list of lobby summaries
     */
    public LobbyListPayload lobbyList(List<Lobby> lobbies) {
        return new LobbyListPayload(lobbies.stream()
                .map(this::summary)
                .toList());
    }

    /**
     * Helper method to map lobby domain details into a standard {@link LobbyUpdatePayload}.
     *
     * @param event the type of event triggering the lobby update (e.g., creation)
     * @param nickname the nickname of the player associated with the event
     * @param message an descriptive message associated with the event
     * @param lobby the {@link Lobby} domain model being mapped
     * @return the constructed {@link LobbyUpdatePayload}
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

    /**
     * Maps a single {@link Lobby} domain model to a light-weight {@link LobbySummaryPayload}.
     *
     * <p>Summaries are typically used to populate the lobby listing page shown to
     * players in the pre-lobby phase before they join any lobby.
     *
     * @param lobby the {@link Lobby} domain object to summarize
     * @return the summarized payload representation of the lobby
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

}
