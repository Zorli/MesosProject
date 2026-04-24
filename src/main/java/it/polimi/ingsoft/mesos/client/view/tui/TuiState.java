package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.util.List;

/**
 * Immutable client-side view state used by the terminal UI.
 */
final class TuiState {
    private final boolean connected;
    private final boolean authenticated;
    private final String nickname;
    private final TuiScreen screen;
    private final Integer currentLobbyId;
    private final String currentLobbyHost;
    private final Integer currentLobbyMaxPlayers;
    private final List<String> currentLobbyPlayers;
    private final LobbyListPayload lastLobbyList;
    private final PreMatchUpdatePayload lastPreMatchUpdate;
    private final ErrorPayload lastError;
    private final GamePayload lastGamePayload;

    /**
     * Creates a new immutable view state snapshot.
     *
     * @param connected whether the client is connected
     * @param authenticated whether the client has authenticated
     * @param nickname current player nickname
     * @param screen current logical screen
     * @param currentLobbyId current lobby identifier
     * @param currentLobbyHost current lobby host
     * @param currentLobbyMaxPlayers current lobby capacity
     * @param currentLobbyPlayers current lobby members
     * @param lastLobbyList last received lobby list
     * @param lastPreMatchUpdate last received pre-match event
     * @param lastError last received error
     * @param lastGamePayload last received game snapshot
     */
    TuiState(
            boolean connected,
            boolean authenticated,
            String nickname,
            TuiScreen screen,
            Integer currentLobbyId,
            String currentLobbyHost,
            Integer currentLobbyMaxPlayers,
            List<String> currentLobbyPlayers,
            LobbyListPayload lastLobbyList,
            PreMatchUpdatePayload lastPreMatchUpdate,
            ErrorPayload lastError,
            GamePayload lastGamePayload
    ) {
        this.connected = connected;
        this.authenticated = authenticated;
        this.nickname = nickname;
        this.screen = screen;
        this.currentLobbyId = currentLobbyId;
        this.currentLobbyHost = currentLobbyHost;
        this.currentLobbyMaxPlayers = currentLobbyMaxPlayers;
        this.currentLobbyPlayers = List.copyOf(currentLobbyPlayers);
        this.lastLobbyList = lastLobbyList;
        this.lastPreMatchUpdate = lastPreMatchUpdate;
        this.lastError = lastError;
        this.lastGamePayload = lastGamePayload;
    }

    /**
     * Builds the initial state used before the UI receives any server payload.
     *
     * @return initial terminal state
     */
    static TuiState initial() {
        return new TuiState(
                false,
                false,
                null,
                TuiScreen.HOME,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null
        );
    }

    /**
     * Returns whether the client is currently connected.
     *
     * @return {@code true} when connected
     */
    boolean isConnected() {
        return this.connected;
    }

    /**
     * Returns whether the client has authenticated with the server.
     *
     * @return {@code true} when authenticated
     */
    boolean isAuthenticated() {
        return this.authenticated;
    }

    /**
     * Returns the current player nickname when known.
     *
     * @return nickname, or {@code null} when unknown
     */
    String getNickname() {
        return this.nickname;
    }

    /**
     * Returns the logical screen to render.
     *
     * @return current screen
     */
    TuiScreen getScreen() {
        return this.screen;
    }

    /**
     * Returns the current lobby identifier.
     *
     * @return lobby identifier, or {@code null} when not in a lobby
     */
    Integer getCurrentLobbyId() {
        return this.currentLobbyId;
    }

    /**
     * Returns the current lobby host nickname.
     *
     * @return host nickname, or {@code null} when unavailable
     */
    String getCurrentLobbyHost() {
        return this.currentLobbyHost;
    }

    /**
     * Returns the current lobby capacity.
     *
     * @return maximum player count, or {@code null} when unavailable
     */
    Integer getCurrentLobbyMaxPlayers() {
        return this.currentLobbyMaxPlayers;
    }

    /**
     * Returns the current lobby members.
     *
     * @return immutable lobby player list
     */
    List<String> getCurrentLobbyPlayers() {
        return this.currentLobbyPlayers;
    }

    /**
     * Returns the last received lobby list payload.
     *
     * @return last lobby list payload, or {@code null} when absent
     */
    LobbyListPayload getLastLobbyList() {
        return this.lastLobbyList;
    }

    /**
     * Returns the last received pre-match payload.
     *
     * @return last pre-match payload, or {@code null} when absent
     */
    PreMatchUpdatePayload getLastPreMatchUpdate() {
        return this.lastPreMatchUpdate;
    }

    /**
     * Returns the last received error payload.
     *
     * @return last error payload, or {@code null} when absent
     */
    ErrorPayload getLastError() {
        return this.lastError;
    }

    /**
     * Returns the last received game payload.
     *
     * @return last game snapshot, or {@code null} when absent
     */
    GamePayload getLastGamePayload() {
        return this.lastGamePayload;
    }

    /**
     * Returns whether a lobby context is currently active.
     *
     * @return {@code true} when the user is in a lobby or has a lobby bootstrap state
     */
    boolean hasLobbyContext() {
        return this.currentLobbyId != null;
    }

    /**
     * Returns whether a full game snapshot is currently available.
     *
     * @return {@code true} when at least one {@link GamePayload} has been received
     */
    boolean hasGameSnapshot() {
        return this.lastGamePayload != null;
    }
}
