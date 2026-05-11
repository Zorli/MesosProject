package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.user.User;
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
 * The absolute Source of Truth for every session detail, active controller mapping, and lobby models.
 */
public class ServerSessionRegistry {
    private static final long TIMEOUT_MILLIS = Duration.ofSeconds(25).toMillis();

    public enum Phase {
        PRE_LOBBY,
        LOBBY,
        GAME
    }

    // --- MAPPE DI SESSIONE ---
    private final Map<String, SessionState> sessionsById = new HashMap<>();
    private final Map<String, String> sessionIdByNickname = new HashMap<>();

    // --- LE "PAGINE GIALLE" DEI CONTROLLER ---
    private final Map<Integer, LobbyController> activeLobbyControllers = new ConcurrentHashMap<>();
    private final Map<Integer, GameOrchestrator> activeGames = new ConcurrentHashMap<>();

    // --- MAPPA E GENERATORE DEI MODEL DELLE LOBBY ---
    private final AtomicInteger nextLobbyId = new AtomicInteger(1);
    private final Map<Integer, Lobby> lobbyModels = new HashMap<>();

    // ==========================================
    // GESTIONE DEI MODEL (Lobby)
    // ==========================================

    public synchronized Lobby createLobbyModel(String hostNickname, int capacity) {
        int lobbyId = this.nextLobbyId.getAndIncrement();
        Lobby lobby = new Lobby(lobbyId, hostNickname, capacity);
        this.lobbyModels.put(lobby.getId(), lobby);
        return lobby;
    }

    public synchronized void registerLobbyModel(Lobby lobby) {
        Objects.requireNonNull(lobby, "lobby cannot be null");
        this.lobbyModels.put(lobby.getId(), lobby);
        this.nextLobbyId.updateAndGet(current -> Math.max(current, lobby.getId() + 1));
    }

    public synchronized Optional<Lobby> getLobbyModel(int lobbyId) {
        return Optional.ofNullable(this.lobbyModels.get(lobbyId));
    }

    public synchronized Lobby requireLobbyModel(int lobbyId) {
        return this.getLobbyModel(lobbyId)
                .orElseThrow(() -> new IllegalArgumentException("Lobby not found: " + lobbyId));
    }

    public synchronized void removeLobbyModel(int lobbyId) {
        this.lobbyModels.remove(lobbyId);
    }

    public synchronized List<Lobby> getOpenLobbies() {
        return this.lobbyModels.values().stream()
                .filter(lobby -> lobby.getStatus() != LobbyStatus.CLOSED)
                .filter(lobby -> lobby.getStatus() != LobbyStatus.STARTED)
                .sorted(Comparator.comparingInt(Lobby::getId))
                .toList();
    }

    public synchronized boolean isPlayerInAnyLobby(String nickname) {
        return this.lobbyModels.values().stream()
                .filter(lobby -> lobby.getStatus() != LobbyStatus.CLOSED)
                .filter(lobby -> lobby.getStatus() != LobbyStatus.STARTED)
                .anyMatch(lobby -> lobby.hasPlayer(nickname));
    }

    // ==========================================
    // REGISTRAZIONE CONTROLLER (Yellow Pages)
    // ==========================================

    public void registerLobbyController(int lobbyId, LobbyController controller) {
        this.activeLobbyControllers.put(lobbyId, Objects.requireNonNull(controller));
    }

    public void removeLobbyController(int lobbyId) {
        this.activeLobbyControllers.remove(lobbyId);
    }

    public LobbyController getLobbyController(int lobbyId) {
        return this.activeLobbyControllers.get(lobbyId);
    }

    public void registerGameOrchestrator(int gameId, GameOrchestrator orchestrator) {
        this.activeGames.put(gameId, Objects.requireNonNull(orchestrator));
    }

    public void removeGameOrchestrator(int gameId) {
        this.activeGames.remove(gameId);
    }

    public GameOrchestrator getGameOrchestrator(int gameId) {
        return this.activeGames.get(gameId);
    }

    // ==========================================
    // GESTIONE CONNESSIONI E AUTENTICAZIONE
    // ==========================================

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

    public synchronized void enterLobby(String sessionId, int lobbyId) {
        SessionState state = this.requireSession(sessionId);
        state.lobbyId = lobbyId;
        state.gameId = null;
        state.phase = Phase.LOBBY;
    }

    public synchronized void leaveLobby(String sessionId) {
        SessionState state = this.requireSession(sessionId);
        state.lobbyId = null;
        state.phase = Phase.PRE_LOBBY;
    }

    public synchronized void enterGame(String sessionId, int gameId) {
        SessionState state = this.requireSession(sessionId);
        state.gameId = gameId;
        state.lobbyId = null; // Pulisce il vecchio riferimento
        state.phase = Phase.GAME;
    }

    public synchronized void leaveGame(String sessionId) {
        SessionState state = this.requireSession(sessionId);
        state.gameId = null;
        state.phase = Phase.PRE_LOBBY;
    }

    // ==========================================
    // LIFECYCLE DELLA SESSIONE
    // ==========================================

    public synchronized void unregister(String sessionId) {
        SessionState state = this.sessionsById.remove(sessionId);
        if (state != null && state.user != null) {
            this.sessionIdByNickname.remove(state.user.getNickname());
        }
    }

    public synchronized void recordPong(String sessionId) {
        SessionState state = this.sessionsById.get(sessionId);
        if (state != null) {
            state.lastPongAt = System.currentTimeMillis();
        }
    }

    // ==========================================
    // GETTERS
    // ==========================================

    public synchronized boolean isAuthenticated(String sessionId) {
        SessionState state = this.sessionsById.get(sessionId);
        return state != null && state.user != null;
    }

    public synchronized Phase getPhaseOf(String sessionId) {
        return this.requireSession(sessionId).phase;
    }

    public synchronized User userOf(String sessionId) {
        return this.requireSession(sessionId).user;
    }

    public synchronized String nicknameOf(String sessionId) {
        User user = this.requireSession(sessionId).user;
        return user == null ? null : user.getNickname();
    }

    public synchronized Integer lobbyIdOf(String sessionId) {
        return this.requireSession(sessionId).lobbyId;
    }
    public synchronized Listener getListenerOf(String sessionId) {
        ClientConnection connection = this.requireSession(sessionId).connection;
        if (connection instanceof Listener listener) {
            return listener;
        }
        throw new IllegalStateException("La connessione per la sessione " + sessionId + " non implementa Listener");
    }
    public synchronized Integer gameIdOf(String sessionId) {
        return this.requireSession(sessionId).gameId;
    }

    public synchronized ClientConnection connectionOf(String sessionId) {
        return this.requireSession(sessionId).connection;
    }

    // ==========================================
    // SNAPSHOTS (Per lettura sicura e iterazioni)
    // ==========================================

    public synchronized SessionSnapshot snapshotOf(String sessionId) {
        return this.toSnapshot(sessionId, this.requireSession(sessionId));
    }

    public synchronized List<SessionSnapshot> snapshots() {
        List<SessionSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, SessionState> entry : this.sessionsById.entrySet()) {
            snapshots.add(this.toSnapshot(entry.getKey(), entry.getValue()));
        }
        return snapshots;
    }

    public synchronized List<SessionSnapshot> sessionsInGame(int gameId) {
        List<SessionSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, SessionState> entry : this.sessionsById.entrySet()) {
            if (entry.getValue().gameId != null && entry.getValue().gameId == gameId) {
                snapshots.add(this.toSnapshot(entry.getKey(), entry.getValue()));
            }
        }
        return snapshots;
    }

    public synchronized List<SessionSnapshot> sessionsInLobby(int lobbyId) {
        List<SessionSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, SessionState> entry : this.sessionsById.entrySet()) {
            if (entry.getValue().lobbyId != null && entry.getValue().lobbyId == lobbyId) {
                snapshots.add(this.toSnapshot(entry.getKey(), entry.getValue()));
            }
        }
        return snapshots;
    }

    private SessionState requireSession(String sessionId) {
        SessionState state = this.sessionsById.get(sessionId);
        if (state == null) {
            throw new IllegalStateException("Unknown session: " + sessionId);
        }
        return state;
    }

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

    private static class SessionState {
        private final ClientConnection connection;
        private User user;
        private Phase phase = Phase.PRE_LOBBY;
        private Integer lobbyId;
        private Integer gameId;
        private long lastPongAt;

        private SessionState(ClientConnection connection, long lastPongAt) {
            this.connection = connection;
            this.lastPongAt = lastPongAt;
        }
    }

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
