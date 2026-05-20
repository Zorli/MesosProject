package it.polimi.ingsoft.mesos.server.controller.server;

import it.polimi.ingsoft.mesos.server.controller.game.GameController;
import it.polimi.ingsoft.mesos.server.controller.lobby.LobbyController;
import it.polimi.ingsoft.mesos.server.virtualview.Listener;
import it.polimi.ingsoft.mesos.server.model.lobby.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.server.User;
import it.polimi.ingsoft.mesos.server.network.ClientConnection;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The absolute Source of Truth for every active session detail, active controller mapping,
 * and lobby models. This class acts as the centralized coordinator for network session states,
 * authentication mapping, game phases, and active orchestrators.
 */
public class ServerSessionRegistry {
    /**
     * Timeout duration in milliseconds after which an inactive session is considered timed out.
     */
    private static final long TIMEOUT_MILLIS = Duration.ofSeconds(8).toMillis();

    /**
     * Represents the distinct phases a user session can be in during their connection lifetime.
     */
    public enum Phase {
        /**
         * Initial phase where the client is authenticating or interacting in the global space.
         */
        PRE_LOBBY,
        /**
         * Phase entered when a client successfully creates or joins a lobby.
         */
        LOBBY,
        /**
         * Active game playing phase after the lobby match has been started.
         */
        GAME
    }

    /**
     * Map storing the active session states indexed by their unique session ID.
     */
    private final Map<String, SessionState> sessionsById = new HashMap<>();

    /**
     * Map mapping authenticated nicknames to their corresponding active session IDs.
     */
    private final Map<String, String> sessionIdByNickname = new HashMap<>();

    /**
     * Map keeping track of disconnected player nicknames and the ID of the game they disconnected from,
     * allowing them to perform a future reconnection to the same game.
     */
    private final Map<String, Integer> DisconnectedNicknameOnGame = new HashMap<>();

    /**
     * Registry map linking active lobby IDs to their respective LobbyController instance.
     */
    private final Map<Integer, LobbyController> activeLobbyControllers = new ConcurrentHashMap<>();

    /**
     * Registry map linking active game IDs to their respective GameController instance.
     */
    private final Map<Integer, GameController> activeGames = new ConcurrentHashMap<>();

    /**
     * Atomic generator used to assign unique and sequential lobby IDs.
     */
    private final AtomicInteger nextLobbyId = new AtomicInteger(1);

    /**
     * Map storing all created Lobby models indexed by their lobby ID.
     */
    private final Map<Integer, Lobby> lobbyModels = new HashMap<>();

    /**
     * Default constructor for ServerSessionRegistry.
     */
    public ServerSessionRegistry() {
    }

    // ==========================================
    // GESTIONE DEI MODEL (Lobby)
    // ==========================================

    /**
     * Creates a new Lobby model, registers it in the registry, and increments the lobby ID counter.
     *
     * @param hostNickname the nickname of the player creating and hosting the lobby
     * @param capacity     the maximum player capacity of the lobby
     * @return the created Lobby instance
     */
    public synchronized Lobby createLobbyModel(String hostNickname, int capacity) {
        int lobbyId = this.nextLobbyId.getAndIncrement();
        Lobby lobby = new Lobby(lobbyId, hostNickname, capacity);
        this.lobbyModels.put(lobby.getId(), lobby);
        return lobby;
    }

    /**
     * Registers an externally created or restored Lobby model.
     *
     * @param lobby the Lobby instance to register
     * @throws NullPointerException     if the lobby is null
     * @throws IllegalArgumentException if a lobby with the same ID is already registered
     */
    public synchronized void registerLobbyModel(Lobby lobby) {
        Objects.requireNonNull(lobby, "lobby cannot be null");
        if (this.lobbyModels.containsKey(lobby.getId())) {
            throw new IllegalArgumentException("Lobby already registered: " + lobby.getId());
        }
        this.lobbyModels.put(lobby.getId(), lobby);
        this.nextLobbyId.updateAndGet(current -> Math.max(current, lobby.getId() + 1));
    }

    /**
     * Retrieves a Lobby model from the registry by its lobby ID.
     *
     * @param lobbyId the ID of the lobby to retrieve
     * @return an Optional containing the lobby if found, otherwise empty
     */
    public synchronized Optional<Lobby> getLobbyModel(int lobbyId) {
        return Optional.ofNullable(this.lobbyModels.get(lobbyId));
    }

    /**
     * Retrieves a Lobby model by its ID or throws an exception if it is not found.
     *
     * @param lobbyId the ID of the lobby to retrieve
     * @return the Lobby instance
     * @throws IllegalArgumentException if the lobby is not found in the registry
     */
    public synchronized Lobby requireLobbyModel(int lobbyId) {
        return this.getLobbyModel(lobbyId)
                .orElseThrow(() -> new IllegalArgumentException("Lobby not found: " + lobbyId));
    }

    /**
     * Removes a Lobby model from the registry.
     *
     * @param lobbyId the ID of the lobby to remove
     */
    public synchronized void removeLobbyModel(int lobbyId) {
        this.lobbyModels.remove(lobbyId);
    }

    /**
     * Returns a list of all currently open lobbies that are neither CLOSED nor STARTED,
     * sorted by their lobby ID.
     *
     * @return a sorted list of open lobbies
     */
    public synchronized List<Lobby> getOpenLobbies() {
        return this.lobbyModels.values().stream()
                .filter(lobby -> lobby.getStatus() != LobbyStatus.CLOSED)
                .filter(lobby -> lobby.getStatus() != LobbyStatus.STARTED)
                .sorted(Comparator.comparingInt(Lobby::getId))
                .toList();
    }

    /**
     * Checks if a player with the given nickname is currently inside any open (unstarted) lobby.
     *
     * @param nickname the nickname of the player to check
     * @return true if the player is in any open lobby, false otherwise
     */
    public synchronized boolean isPlayerInAnyLobby(String nickname) {
        return this.lobbyModels.values().stream()
                .filter(lobby -> lobby.getStatus() != LobbyStatus.CLOSED)
                .filter(lobby -> lobby.getStatus() != LobbyStatus.STARTED)
                .anyMatch(lobby -> lobby.hasPlayer(nickname));
    }

    // ==========================================
    // REGISTRAZIONE CONTROLLER (Yellow Pages)
    // ==========================================

    /**
     * Registers a LobbyController for a specific lobby.
     *
     * @param lobbyId    the ID of the lobby
     * @param controller the controller instance handling lobby commands
     */
    public void registerLobbyController(int lobbyId, LobbyController controller) {
        this.activeLobbyControllers.put(lobbyId, Objects.requireNonNull(controller));
    }

    /**
     * Removes a registered LobbyController from the registry.
     *
     * @param lobbyId the ID of the lobby to remove the controller for
     */
    public void removeLobbyController(int lobbyId) {
        this.activeLobbyControllers.remove(lobbyId);
    }

    /**
     * Retrieves the registered LobbyController for a specific lobby.
     *
     * @param lobbyId the ID of the lobby
     * @return the LobbyController instance, or null if not found
     */
    public LobbyController getLobbyController(int lobbyId) {
        return this.activeLobbyControllers.get(lobbyId);
    }

    /**
     * Registers a GameController orchestrator for a specific active game.
     *
     * @param gameId       the ID of the game
     * @param orchestrator the controller instance orchestrating game rules and commands
     */
    public void registerGameOrchestrator(int gameId, GameController orchestrator) {
        this.activeGames.put(gameId, Objects.requireNonNull(orchestrator));
    }

    /**
     * Removes a registered GameController orchestrator from the registry.
     *
     * @param gameId the ID of the game to remove the orchestrator for
     */
    public void removeGameOrchestrator(int gameId) {
        this.activeGames.remove(gameId);
    }

    /**
     * Retrieves the registered GameController orchestrator for a specific game.
     *
     * @param gameId the ID of the game
     * @return the GameController instance, or null if not found
     */
    public GameController getGameOrchestrator(int gameId) {
        return this.activeGames.get(gameId);
    }

    // ==========================================
    // GESTIONE CONNESSIONI E AUTENTICAZIONE
    // ==========================================

    /**
     * Registers a newly established client connection in the registry, assigning it a unique session ID.
     *
     * @param connection the ClientConnection instance representing the client's network link
     * @return the generated unique session ID
     * @throws NullPointerException if the connection is null
     */
    public synchronized String registerConnection(ClientConnection connection) {
        Objects.requireNonNull(connection, "connection cannot be null");
        String sessionId;
        do {
            sessionId = UUID.randomUUID().toString();
        } while (this.sessionsById.containsKey(sessionId));

        SessionState state = new SessionState(connection, System.currentTimeMillis());
        this.sessionsById.put(sessionId, state);
        return sessionId;
    }

    /**
     * Registers that the authenticated session left a running game.
     *
     * @param sessionId the disconnected session ID
     * @param gameId    the game ID that can accept a later reconnect from this player
     */
    public synchronized void RegisterDisconnection(String sessionId, int gameId) {
        String nickname = this.nicknameOf(sessionId);
        this.registerDisconnection(nickname, gameId);
    }

    /**
     * Registers a disconnected nickname for a running game.
     * This overload is used when the old session is already being removed but
     * the server still knows which nickname disconnected.
     *
     * @param nickname disconnected player nickname
     * @param gameId   game that can accept a later reconnect
     */
    public synchronized void registerDisconnection(String nickname, int gameId) {
        if (nickname == null || nickname.isBlank()) {
            return;
        }
        this.DisconnectedNicknameOnGame.put(nickname, gameId);
    }

    /**
     * Checks whether an old disconnect command has been superseded by a reconnect.
     * The replacement session does not need to be in {@link Phase#GAME} yet.
     * During a fast reconnect, the new session can already be authenticated while
     * its {@code SystemJoinCommand} is still waiting behind the old disconnect in
     * the game queue. For resilience purposes that authenticated replacement is
     * already enough to make the old disconnect stale.
     *
     * @param nickname     player nickname carried by the old disconnect command
     * @param gameId       game that owns the queued disconnect
     * @param oldSessionId session that produced the old disconnect
     * @return true when a newer authenticated session owns the nickname
     * and is either waiting to rejoin this game or has already rejoined it
     */
    public synchronized boolean isReconnectedInGame(String nickname, int gameId, String oldSessionId) {
        String currentSessionId = this.sessionIdByNickname.get(nickname);
        if (currentSessionId == null || currentSessionId.equals(oldSessionId)) {
            return false;
        }

        SessionState currentSession = this.sessionsById.get(currentSessionId);
        if (currentSession == null || currentSession.user == null) {
            return false;
        }

        boolean pendingReconnect = Objects.equals(this.DisconnectedNicknameOnGame.get(nickname), gameId);
        boolean completedReconnect = currentSession.phase == Phase.GAME
                && Objects.equals(currentSession.gameId, gameId);
        return pendingReconnect || completedReconnect;
    }

    /**
     * Moves an authenticated reconnecting session back into its previous game.
     *
     * @param sessionId the new authenticated session ID
     * @throws IllegalStateException if there is no pending disconnected game recorded for the nickname
     */
    public synchronized void RegisterReConnection(String sessionId) {
        String nickname = this.nicknameOf(sessionId);
        Integer gameId = this.DisconnectedNicknameOnGame.get(nickname);
        if (gameId == null) {
            throw new IllegalStateException("No disconnected game for nickname: " + nickname);
        }
        this.DisconnectedNicknameOnGame.remove(nickname);
        this.enterGame(sessionId, gameId);
    }

    /**
     * Removes all disconnected nickname references associated with a specific game.
     *
     * @param gameId the ID of the game
     */
    public synchronized void removeDisconnectedReference(int gameId) {
        this.DisconnectedNicknameOnGame.values().removeIf(id -> Integer.valueOf(gameId).equals(id));
    }

    /**
     * Removes a disconnected nickname reference.
     *
     * @param nickname the nickname of the player to remove
     */
    public synchronized void removeDisconnectedReference(String nickname) {
        this.DisconnectedNicknameOnGame.remove(nickname);
    }

    /**
     * Authenticates a session with a unique nickname, creating and linking a User model.
     *
     * @param sessionId the session ID to authenticate
     * @param nickname  the chosen nickname
     * @return the created User model
     * @throws IllegalStateException    if the session is already authenticated
     * @throws IllegalArgumentException if the nickname is null/empty or is already taken by another session
     */
    public synchronized User authenticate(String sessionId, String nickname) {
        SessionState state = this.requireSession(sessionId);
        if (state.user != null) {
            throw new IllegalStateException("Client is already authenticated");
        }
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("Nickname cannot be empty");
        }
        if (this.sessionIdByNickname.containsKey(nickname)) {
            throw new IllegalArgumentException("Nickname already taken: " + nickname);
        }
        User user = new User(nickname);
        state.user = user;
        this.sessionIdByNickname.put(nickname, sessionId);
        return user;
    }

    // ==========================================
    // TRANSIZIONI DI FASE MACCHINA A STATI
    // ==========================================

    /**
     * Transitions an authenticated session state into the LOBBY phase.
     *
     * @param sessionId the ID of the session
     * @param lobbyId   the ID of the lobby being entered
     */
    public synchronized void enterLobby(String sessionId, int lobbyId) {
        SessionState state = this.requireSession(sessionId);
        state.lobbyId = lobbyId;
        state.gameId = null;
        state.phase = Phase.LOBBY;
    }

    /**
     * Transitions an authenticated session state out of the lobby back to the PRE_LOBBY phase.
     *
     * @param sessionId the ID of the session
     */
    public synchronized void leaveLobby(String sessionId) {
        SessionState state = this.requireSession(sessionId);
        state.lobbyId = null;
        state.phase = Phase.PRE_LOBBY;
    }

    /**
     * Transitions an authenticated session state into the GAME phase.
     *
     * @param sessionId the ID of the session
     * @param gameId    the ID of the game being entered
     */
    public synchronized void enterGame(String sessionId, int gameId) {
        SessionState state = this.requireSession(sessionId);
        state.gameId = gameId;
        state.lobbyId = null;
        state.phase = Phase.GAME;
    }

    /**
     * Transitions an authenticated session state out of the game back to the PRE_LOBBY phase.
     *
     * @param sessionId the ID of the session
     */
    public synchronized void leaveGame(String sessionId) {
        SessionState state = this.requireSession(sessionId);
        state.gameId = null;
        state.phase = Phase.PRE_LOBBY;
    }

    // ==========================================
    // LIFECYCLE DELLA SESSIONE
    // ==========================================

    /**
     * Unregisters a session and removes all associated mappings from the registry.
     *
     * @param sessionId the session ID to remove
     */
    public synchronized void unregister(String sessionId) {
        SessionState state = this.sessionsById.remove(sessionId);
        if (state != null && state.user != null) {
            this.sessionIdByNickname.remove(state.user.getNickname());
        }
    }

    /**
     * Records the arrival time of a network pong message for timeout calculations.
     *
     * @param sessionId the session ID that sent the pong
     */
    public synchronized void recordPong(String sessionId) {
        SessionState state = this.sessionsById.get(sessionId);
        if (state != null) {
            state.lastPongAt = System.currentTimeMillis();
        }
    }

    // ==========================================
    // GETTERS
    // ==========================================

    /**
     * Checks whether a session has been successfully authenticated with a nickname.
     *
     * @param sessionId the session ID to query
     * @return true if authenticated, false otherwise
     */
    public synchronized boolean isAuthenticated(String sessionId) {
        SessionState state = this.sessionsById.get(sessionId);
        return state != null && state.user != null;
    }

    /**
     * Checks if a nickname is registered as disconnected from an ongoing game.
     *
     * @param nickname the nickname to query
     * @return true if registered as disconnected, false otherwise
     */
    public synchronized boolean isDisconnected(String nickname) {
        return this.DisconnectedNicknameOnGame.containsKey(nickname);
    }

    /**
     * Retrieves the game ID from which a player disconnected.
     *
     * @param nickname the nickname of the player
     * @return the game ID
     */
    public synchronized int DisconnectedFrom(String nickname) {
        return this.DisconnectedNicknameOnGame.get(nickname);
    }

    /**
     * Retrieves the current phase of a session.
     *
     * @param sessionId the session ID to query
     * @return the Phase the session is in
     */
    public synchronized Phase getPhaseOf(String sessionId) {
        return this.requireSession(sessionId).phase;
    }

    /**
     * Retrieves the User model associated with a session.
     *
     * @param sessionId the session ID to query
     * @return the User model
     */
    public synchronized User userOf(String sessionId) {
        return this.requireSession(sessionId).user;
    }

    /**
     * Retrieves the nickname associated with a session.
     *
     * @param sessionId the session ID to query
     * @return the nickname string, or null if the session is not authenticated
     */
    public synchronized String nicknameOf(String sessionId) {
        User user = this.requireSession(sessionId).user;
        return user == null ? null : user.getNickname();
    }

    /**
     * Retrieves the lobby ID associated with a session.
     *
     * @param sessionId the session ID to query
     * @return the lobby ID, or null if not in a lobby
     */
    public synchronized Integer lobbyIdOf(String sessionId) {
        return this.requireSession(sessionId).lobbyId;
    }

    /**
     * Casts the session's ClientConnection to a Listener interface.
     *
     * @param sessionId the session ID to query
     * @return the connection cast as Listener
     * @throws IllegalStateException if the connection does not implement the Listener interface
     */
    public synchronized Listener getListenerOf(String sessionId) {
        ClientConnection connection = this.requireSession(sessionId).connection;
        if (connection instanceof Listener listener) {
            return listener;
        }
        throw new IllegalStateException("La connessione per la sessione " + sessionId + " non implementa Listener");
    }

    /**
     * Retrieves the game ID associated with a session.
     *
     * @param sessionId the session ID to query
     * @return the game ID, or null if not in a game
     */
    public synchronized Integer gameIdOf(String sessionId) {
        return this.requireSession(sessionId).gameId;
    }

    /**
     * Retrieves the connection object associated with a session.
     *
     * @param sessionId the session ID to query
     * @return the ClientConnection instance
     */
    public synchronized ClientConnection connectionOf(String sessionId) {
        return this.requireSession(sessionId).connection;
    }

    // ==========================================
    // SNAPSHOTS (Per lettura sicura e iterazioni)
    // ==========================================

    /**
     * Creates an immutable SessionSnapshot representing the state of a session.
     *
     * @param sessionId the ID of the session
     * @return a snapshot of the session state
     */
    public synchronized SessionSnapshot snapshotOf(String sessionId) {
        return this.toSnapshot(sessionId, this.requireSession(sessionId));
    }

    /**
     * Generates immutable SessionSnapshot representations of all active sessions.
     *
     * @return a list of snapshots for all registered sessions
     */
    public synchronized List<SessionSnapshot> snapshots() {
        List<SessionSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, SessionState> entry : this.sessionsById.entrySet()) {
            snapshots.add(this.toSnapshot(entry.getKey(), entry.getValue()));
        }
        return snapshots;
    }

    /**
     * Generates snapshots of all active sessions participating in a specific game.
     *
     * @param gameId the ID of the game
     * @return a list of matching session snapshots
     */
    public synchronized List<SessionSnapshot> sessionsInGame(int gameId) {
        List<SessionSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, SessionState> entry : this.sessionsById.entrySet()) {
            if (Objects.equals(entry.getValue().gameId, gameId)) {
                snapshots.add(this.toSnapshot(entry.getKey(), entry.getValue()));
            }
        }
        return snapshots;
    }

    /**
     * Generates snapshots of all active sessions participating in a specific lobby.
     *
     * @param lobbyId the ID of the lobby
     * @return a list of matching session snapshots
     */
    public synchronized List<SessionSnapshot> sessionsInLobby(int lobbyId) {
        List<SessionSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, SessionState> entry : this.sessionsById.entrySet()) {
            if (Objects.equals(entry.getValue().lobbyId, lobbyId)) {
                snapshots.add(this.toSnapshot(entry.getKey(), entry.getValue()));
            }
        }
        return snapshots;
    }

    /**
     * Finds a session by ID and throws an exception if it does not exist.
     *
     * @param sessionId the ID of the session
     * @return the SessionState instance
     * @throws IllegalStateException if the session ID is not registered
     */
    private SessionState requireSession(String sessionId) {
        SessionState state = this.sessionsById.get(sessionId);
        if (state == null) {
            throw new IllegalStateException("Unknown session: " + sessionId);
        }
        return state;
    }

    /**
     * Converts a mutable SessionState to an immutable SessionSnapshot.
     */
    private SessionSnapshot toSnapshot(String sessionId, SessionState state) {
        return new SessionSnapshot(
                sessionId,
                state.connection,
                state.user,
                state.phase,
                state.lobbyId,
                state.gameId,
                state.lastPongAt,
                System.currentTimeMillis() - state.lastPongAt > TIMEOUT_MILLIS
        );
    }

    // ==========================================
    // CLASSI INTERNE
    // ==========================================

    /**
     * Represents the internal, mutable state of a registered user session.
     */
    private static class SessionState {
        /**
         * The network link connection for this session.
         */
        private final ClientConnection connection;
        /**
         * The authenticated User details, or null if not yet authenticated.
         */
        private User user;
        /**
         * The current phase of the session, defaults to PRE_LOBBY.
         */
        private Phase phase = Phase.PRE_LOBBY;
        /**
         * The ID of the lobby this session belongs to, or null.
         */
        private Integer lobbyId;
        /**
         * The ID of the game this session belongs to, or null.
         */
        private Integer gameId;
        /**
         * The timestamp in milliseconds when the last network pong was received.
         */
        private long lastPongAt;

        /**
         * Constructs a SessionState.
         *
         * @param connection the connection link
         * @param lastPongAt the initial pong timestamp
         */
        private SessionState(ClientConnection connection, long lastPongAt) {
            this.connection = connection;
            this.lastPongAt = lastPongAt;
        }
    }

    /**
     * Immutable snapshot representation of a session state, suitable for secure access and iterations.
     *
     * @param sessionId  the unique session ID
     * @param connection the active network connection link
     * @param user       the user model (null if unauthenticated)
     * @param phase      the current session phase
     * @param lobbyId    the lobby ID (null if not in lobby)
     * @param gameId     the game ID (null if not in game)
     * @param lastPongAt the timestamp of the last recorded network pong
     * @param timedOut   true if the duration since last pong exceeds the threshold timeout limit
     */
    public record SessionSnapshot(
            String sessionId,
            ClientConnection connection,
            User user,
            Phase phase,
            Integer lobbyId,
            Integer gameId,
            long lastPongAt,
            boolean timedOut
    ) {}
}
