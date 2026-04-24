package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchEventType;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.util.List;
import java.util.Objects;

/**
 * Pure reducer that transforms server payloads and local UI intents into {@link TuiState}.
 */
final class TuiStateReducer {
    /**
     * Applies a connection-state refresh.
     *
     * @param currentState previous state
     * @param connected latest connection status
     * @return updated state
     */
    TuiState reduceConnection(TuiState currentState, boolean connected) {
        Objects.requireNonNull(currentState, "currentState cannot be null");
        return this.copy(
                currentState,
                connected,
                currentState.isAuthenticated(),
                currentState.getNickname(),
                currentState.getScreen(),
                currentState.getCurrentLobbyId(),
                currentState.getCurrentLobbyHost(),
                currentState.getCurrentLobbyMaxPlayers(),
                currentState.getCurrentLobbyPlayers(),
                currentState.getLastLobbyList(),
                currentState.getLastPreMatchUpdate(),
                currentState.getLastError(),
                currentState.getLastGamePayload()
        );
    }

    /**
     * Switches the UI to the help screen.
     *
     * @param currentState previous state
     * @return updated state
     */
    TuiState reduceShowHelp(TuiState currentState) {
        Objects.requireNonNull(currentState, "currentState cannot be null");
        return this.copy(
                currentState,
                currentState.isConnected(),
                currentState.isAuthenticated(),
                currentState.getNickname(),
                TuiScreen.HELP,
                currentState.getCurrentLobbyId(),
                currentState.getCurrentLobbyHost(),
                currentState.getCurrentLobbyMaxPlayers(),
                currentState.getCurrentLobbyPlayers(),
                currentState.getLastLobbyList(),
                currentState.getLastPreMatchUpdate(),
                currentState.getLastError(),
                currentState.getLastGamePayload()
        );
    }

    /**
     * Restores the default screen associated with the current data snapshot.
     *
     * @param currentState previous state
     * @return updated state
     */
    TuiState reduceShowStatus(TuiState currentState) {
        Objects.requireNonNull(currentState, "currentState cannot be null");
        return this.copy(
                currentState,
                currentState.isConnected(),
                currentState.isAuthenticated(),
                currentState.getNickname(),
                this.determineStatusScreen(currentState),
                currentState.getCurrentLobbyId(),
                currentState.getCurrentLobbyHost(),
                currentState.getCurrentLobbyMaxPlayers(),
                currentState.getCurrentLobbyPlayers(),
                currentState.getLastLobbyList(),
                currentState.getLastPreMatchUpdate(),
                currentState.getLastError(),
                currentState.getLastGamePayload()
        );
    }

    /**
     * Applies one pre-match update payload.
     *
     * @param currentState previous state
     * @param payload incoming payload
     * @return updated state
     */
    TuiState reducePreMatchUpdate(TuiState currentState, PreMatchUpdatePayload payload) {
        Objects.requireNonNull(currentState, "currentState cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");

        boolean authenticated = currentState.isAuthenticated();
        String nickname = currentState.getNickname();
        Integer currentLobbyId = currentState.getCurrentLobbyId();
        String currentLobbyHost = currentState.getCurrentLobbyHost();
        Integer currentLobbyMaxPlayers = currentState.getCurrentLobbyMaxPlayers();
        List<String> currentLobbyPlayers = currentState.getCurrentLobbyPlayers();
        GamePayload lastGamePayload = currentState.getLastGamePayload();
        TuiScreen screen = currentState.getScreen();

        if (payload.event == PreMatchEventType.AUTHENTICATED) {
            authenticated = true;
            nickname = payload.nickname;
            lastGamePayload = null;
            screen = TuiScreen.HOME;
        } else if (payload.event == PreMatchEventType.LOBBY_CREATED
                || payload.event == PreMatchEventType.LOBBY_JOINED) {
            currentLobbyId = payload.lobbyId;
            currentLobbyHost = payload.hostNickname;
            currentLobbyMaxPlayers = payload.maxPlayers;
            currentLobbyPlayers = copyPlayers(payload.players);
            lastGamePayload = null;
            screen = TuiScreen.WAITING_ROOM;
        } else if (payload.event == PreMatchEventType.LOBBY_LEFT) {
            if (nickname != null && nickname.equals(payload.nickname)) {
                currentLobbyId = null;
                currentLobbyHost = null;
                currentLobbyMaxPlayers = null;
                currentLobbyPlayers = List.of();
                lastGamePayload = null;
                screen = TuiScreen.HOME;
            } else {
                currentLobbyId = payload.lobbyId;
                currentLobbyHost = payload.hostNickname;
                currentLobbyMaxPlayers = payload.maxPlayers;
                currentLobbyPlayers = copyPlayers(payload.players);
                screen = TuiScreen.WAITING_ROOM;
            }
        } else if (payload.event == PreMatchEventType.LOBBY_STARTED) {
            currentLobbyId = payload.lobbyId;
            currentLobbyHost = payload.hostNickname;
            currentLobbyMaxPlayers = payload.maxPlayers;
            currentLobbyPlayers = copyPlayers(payload.players);
            screen = TuiScreen.MATCH_BOOTSTRAP;
        }

        return this.copy(
                currentState,
                currentState.isConnected(),
                authenticated,
                nickname,
                screen,
                currentLobbyId,
                currentLobbyHost,
                currentLobbyMaxPlayers,
                currentLobbyPlayers,
                currentState.getLastLobbyList(),
                payload,
                null,
                lastGamePayload
        );
    }

    /**
     * Applies a lobby list payload.
     *
     * @param currentState previous state
     * @param payload incoming payload
     * @return updated state
     */
    TuiState reduceLobbyList(TuiState currentState, LobbyListPayload payload) {
        Objects.requireNonNull(currentState, "currentState cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");
        return this.copy(
                currentState,
                currentState.isConnected(),
                currentState.isAuthenticated(),
                currentState.getNickname(),
                currentState.hasGameSnapshot() ? TuiScreen.IN_MATCH : TuiScreen.LOBBY_BROWSER,
                currentState.getCurrentLobbyId(),
                currentState.getCurrentLobbyHost(),
                currentState.getCurrentLobbyMaxPlayers(),
                currentState.getCurrentLobbyPlayers(),
                payload,
                currentState.getLastPreMatchUpdate(),
                null,
                currentState.getLastGamePayload()
        );
    }

    /**
     * Applies a game snapshot payload.
     *
     * @param currentState previous state
     * @param payload incoming payload
     * @return updated state
     */
    TuiState reduceGameUpdate(TuiState currentState, GamePayload payload) {
        Objects.requireNonNull(currentState, "currentState cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");
        return this.copy(
                currentState,
                currentState.isConnected(),
                currentState.isAuthenticated(),
                currentState.getNickname(),
                TuiScreen.IN_MATCH,
                currentState.getCurrentLobbyId(),
                currentState.getCurrentLobbyHost(),
                currentState.getCurrentLobbyMaxPlayers(),
                currentState.getCurrentLobbyPlayers(),
                currentState.getLastLobbyList(),
                currentState.getLastPreMatchUpdate(),
                null,
                payload
        );
    }

    /**
     * Applies one error payload.
     *
     * @param currentState previous state
     * @param payload incoming payload
     * @return updated state
     */
    TuiState reduceError(TuiState currentState, ErrorPayload payload) {
        Objects.requireNonNull(currentState, "currentState cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");
        boolean connected = currentState.isConnected();
        if ("CONNECTION_CLOSED".equals(payload.code) || "DISCONNECTED".equals(payload.code)) {
            connected = false;
        }
        return this.copy(
                currentState,
                connected,
                currentState.isAuthenticated(),
                currentState.getNickname(),
                currentState.getScreen(),
                currentState.getCurrentLobbyId(),
                currentState.getCurrentLobbyHost(),
                currentState.getCurrentLobbyMaxPlayers(),
                currentState.getCurrentLobbyPlayers(),
                currentState.getLastLobbyList(),
                currentState.getLastPreMatchUpdate(),
                payload,
                currentState.getLastGamePayload()
        );
    }

    /**
     * Derives the default status screen from the currently cached data.
     *
     * @param currentState current state snapshot
     * @return most appropriate screen for the current data
     */
    private TuiScreen determineStatusScreen(TuiState currentState) {
        if (currentState.hasGameSnapshot()) {
            return TuiScreen.IN_MATCH;
        }
        if (currentState.getLastPreMatchUpdate() != null
                && currentState.getLastPreMatchUpdate().event == PreMatchEventType.LOBBY_STARTED) {
            return TuiScreen.MATCH_BOOTSTRAP;
        }
        if (currentState.hasLobbyContext()) {
            return TuiScreen.WAITING_ROOM;
        }
        if (currentState.getLastLobbyList() != null) {
            return TuiScreen.LOBBY_BROWSER;
        }
        return TuiScreen.HOME;
    }

    /**
     * Creates an immutable defensive copy of the provided player list.
     *
     * @param players incoming player list
     * @return immutable player list
     */
    private static List<String> copyPlayers(List<String> players) {
        return players == null ? List.of() : List.copyOf(players);
    }

    /**
     * Builds a new immutable state snapshot.
     *
     * @param currentState previous state, used only to preserve the immutable type boundary
     * @param connected whether the client is connected
     * @param authenticated whether the client is authenticated
     * @param nickname current player nickname
     * @param screen current screen
     * @param currentLobbyId current lobby id
     * @param currentLobbyHost current lobby host
     * @param currentLobbyMaxPlayers current lobby capacity
     * @param currentLobbyPlayers current lobby members
     * @param lastLobbyList last lobby list
     * @param lastPreMatchUpdate last pre-match payload
     * @param lastError last error payload
     * @param lastGamePayload last game snapshot
     * @return updated immutable state
     */
    private TuiState copy(
            TuiState currentState,
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
        Objects.requireNonNull(currentState, "currentState cannot be null");
        return new TuiState(
                connected,
                authenticated,
                nickname,
                screen,
                currentLobbyId,
                currentLobbyHost,
                currentLobbyMaxPlayers,
                currentLobbyPlayers,
                lastLobbyList,
                lastPreMatchUpdate,
                lastError,
                lastGamePayload
        );
    }
}
