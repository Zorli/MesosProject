package it.polimi.ingsoft.mesos.server.model.preLobby;

import it.polimi.ingsoft.mesos.server.helper.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.network.interfaces.Listener;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;

import java.util.List;

/**
 * Entry point for the server's pre-lobby business logic and session coordination.
 *
 * <p>This model class handles client requests before they transition into a specific
 * lobby or match controller. It coordinates user authentication, lobby list queries,
 * lobby creation, and lobby joining checks.
 *
 * <p>The model acts as a pure MVC component, validating requests against the shared
 * registry {@link ServerSessionRegistry} and directly pushing updates or unicast errors
 * to the connected client {@link Listener} interfaces.
 */
public class preLobbyModel {
    /** Registry containing all session-scoped routing data, active users, and matches. */
    private final ServerSessionRegistry sessions;
    /** Data mapper to convert internal models/events into serializable network payloads. */
    private final PreLobbyPayloadMapper payloadMapper;
    /**
     * Constructs a new preLobbyModel.
     *
     * @param sessions the centralized server session registry
     */
    public preLobbyModel(ServerSessionRegistry sessions) {
        this.sessions = sessions;
        this.payloadMapper = new PreLobbyPayloadMapper();
    }
    /**
     * Sends a targeted error payload to the listener associated with the specified session.
     *
     * @param sessionId the unique identifier of the target client session
     * @param errorType the type classification of the error
     * @param message a descriptive error message explaining the failure
     */
    private void sendUnicastError(String sessionId, String errorType, String message) {
        Listener listener = this.sessions.getListenerOf(sessionId);
        if (listener != null) {
            listener.notifyUpdate(new ErrorPayload(errorType, message));
        }
    }

    /**
     * Pushes a phase error notification to the client indicating their requested action
     * is not permitted in the current state.
     *
     * @param sessionId the unique identifier of the client session
     */
    public void reportPhaseError(String sessionId) {
        this.sendUnicastError(sessionId, "PHASE_ERROR", "Action non permitted in this phase.");
    }

    /**
     * Pushes a joining error notification with a custom message to the client.
     *
     * @param sessionId the unique identifier of the client session
     * @param message the descriptive reason for the joining failure
     */
    public void reportJoinError(String sessionId, String message) {
        this.sendUnicastError(sessionId, "JOIN_ERROR", message);
    }


    // --- BUSINESS METHODS (PRE-LOBBY PHASE) ---

    /**
     * Authenticates a client session with a unique nickname.
     *
     * <p>If authentication is successful, the nickname is registered in the central
     * session registry and a success payload is sent to the client. If authentication fails
     * (e.g., the nickname is already taken or invalid), an error payload is sent.
     *
     * @param sessionId the unique identifier of the client session
     * @param nickname the nickname chosen by the user
     * @return {@code true} if the authentication succeeded, {@code false} otherwise
     */
    public boolean authenticate(String sessionId, String nickname) {
        Listener listener = this.sessions.getListenerOf(sessionId);
        try {
            this.sessions.authenticate(sessionId, nickname);
            listener.notifyUpdate(this.payloadMapper.authenticated(nickname));
            return true;

        } catch (IllegalArgumentException | IllegalStateException e) {
            listener.notifyUpdate(new ErrorPayload("AUTH_ERROR", e.getMessage()));
            return false;
        }
    }

    /**
     * Retrieves the list of currently open/joinable lobbies and pushes it to the client.
     *
     * @param sessionId the unique identifier of the requesting client session
     */
    public void listOpenLobbies(String sessionId) {
        Listener listener = this.sessions.getListenerOf(sessionId);
        try {
            List<Lobby> openLobbies = this.sessions.getOpenLobbies();
            listener.notifyUpdate(this.payloadMapper.lobbyList(openLobbies));

        } catch (Exception e) {
           listener.notifyUpdate(new ErrorPayload("LIST_OPEN_ERROR", e.getMessage()));
        }
    }

    /**
     * Creates a new lobby with the requested player capacity and registers the host.
     *
     * <p>This method ensures the requesting session is eligible to enter a lobby, resolves
     * its authenticated nickname, creates the lobby domain aggregate in the session registry,
     * registers the creator's listener as an observer on the lobby, and returns the new lobby instance.
     *
     * @param sessionId the unique identifier of the client session requesting creation
     * @param chosenCapacity the maximum player capacity of the new lobby
     * @return the newly created {@link Lobby} instance, or {@code null} if creation failed
     */
    public Lobby createLobby(String sessionId, int chosenCapacity) {
        Listener listener = this.sessions.getListenerOf(sessionId);
        try {
            this.ensureSessionCanEnterLobby(sessionId);
            String nickname = this.requireNickname(sessionId);
            Lobby lobby = this.sessions.createLobbyModel(nickname, chosenCapacity);

            lobby.addListener(listener);
            listener.notifyUpdate(this.payloadMapper.lobbyCreated(nickname, lobby));

            return lobby;

        } catch (IllegalArgumentException | IllegalStateException e) {
            listener.notifyUpdate(new ErrorPayload("CREATE_LOBBY_ERROR", e.getMessage()));
            return null;
        }
    }

    /**
     * Validates if a client session is permitted to join the specified lobby.
     *
     * <p>Performs checks to assert that:
     * <ul>
     *     <li>The session is not already associated with any lobby;</li>
     *     <li>The session has authenticated and has an active nickname;</li>
     *     <li>The target lobby exists;</li>
     *     <li>The user nickname is not registered in another active lobby;</li>
     *     <li>The target lobby is in a joinable status.</li>
     * </ul>
     *
     * @param sessionId the unique identifier of the client session attempting to join
     * @param lobbyId the identifier of the target lobby to join
     * @return {@code true} if all validation checks pass, {@code false} otherwise (triggers an error payload unicast)
     */
    public boolean joinLobbyValidation(String sessionId, int lobbyId) {
        Listener listener = this.sessions.getListenerOf(sessionId);
        try {
            if (this.sessions.lobbyIdOf(sessionId) != null) {
                throw new IllegalStateException("Already in a lobby.");
            }
            String nickname = this.requireNickname(sessionId);
            Lobby lobby = this.sessions.getLobbyModel(lobbyId)
                    .orElseThrow(() -> new IllegalArgumentException("La lobby richiesta non esiste."));
            if (this.sessions.isPlayerInAnyLobby(nickname) && !lobby.hasPlayer(nickname)) {
                throw new IllegalArgumentException("Your nickname is already in a lobby.");
            }
            if (!lobby.canBeJoinedBy(nickname)) {
                throw new IllegalStateException("Cannot join lobby with status: " + lobby.getStatus());
            }
            return true;
        } catch (Exception e) {
            listener.notifyUpdate(new ErrorPayload("JOIN_ERROR" , e.getMessage()));
            return false;
        }
    }

    // --- PRIVATE DOMAIN CHECKS ---

    /**
     * Asserts that a session and its associated nickname are not already registered in any lobby.
     *
     * @param sessionId the unique identifier of the client session
     * @throws IllegalStateException if the session or nickname is already associated with a lobby
     */
    private void ensureSessionCanEnterLobby(String sessionId) {
        if (this.sessions.lobbyIdOf(sessionId) != null) {
            throw new IllegalStateException("Already in a lobby.");
        }
        String nickname = this.requireNickname(sessionId);
        if (this.sessions.isPlayerInAnyLobby(nickname)) {
            throw new IllegalStateException("Your nickname is already in a lobby.");
        }
    }

    /**
     * Resolves the authenticated nickname for a session, throwing an exception if not authenticated.
     *
     * @param sessionId the unique identifier of the client session
     * @return the authenticated nickname
     * @throws IllegalStateException if no nickname is associated with this session in the registry
     */
    private String requireNickname(String sessionId) {
        String nickname = this.sessions.nicknameOf(sessionId);
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalStateException("Must first authenticate with nickname.");
        }
        return nickname;
    }
}
