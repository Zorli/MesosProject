package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.user.User;
import it.polimi.ingsoft.mesos.server.network.PingPongConnection;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ServerSessionRegistry {
    private static final long TIMEOUT_MILLIS = Duration.ofSeconds(25).toMillis();

    private final Map<String, SessionState> sessionsById = new HashMap<>();
    private final Map<String, String> sessionIdByNickname = new HashMap<>();

    public synchronized String registerConnection(PingPongConnection connection) {
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

    public synchronized void enterLobby(String sessionId, int lobbyId) {
        this.requireSession(sessionId).lobbyId = lobbyId;
    }

    public synchronized void leaveLobby(String sessionId) {
        this.requireSession(sessionId).lobbyId = null;
    }

    public synchronized void enterGame(String sessionId, GameOrchestrator gameOrchestrator) {
        this.requireSession(sessionId).gameOrchestrator = Objects.requireNonNull(gameOrchestrator, "gameOrchestrator cannot be null");
    }

    public synchronized void leaveGame(String sessionId) {
        this.requireSession(sessionId).gameOrchestrator = null;
    }

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

    public synchronized boolean isAuthenticated(String sessionId) {
        SessionState state = this.sessionsById.get(sessionId);
        return state != null && state.user != null;
    }

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

    public synchronized List<SessionSnapshot> sessionsInGame(GameOrchestrator gameOrchestrator) {
        List<SessionSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, SessionState> entry : this.sessionsById.entrySet()) {
            if (entry.getValue().gameOrchestrator == gameOrchestrator) {
                snapshots.add(this.toSnapshot(entry.getKey(), entry.getValue()));
            }
        }
        return snapshots;
    }

    public synchronized List<SessionSnapshot> sessionsInLobby(int lobbyId) {
        List<SessionSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, SessionState> entry : this.sessionsById.entrySet()) {
            Integer currentLobbyId = entry.getValue().lobbyId;
            if (currentLobbyId != null && currentLobbyId == lobbyId) {
                snapshots.add(this.toSnapshot(entry.getKey(), entry.getValue()));
            }
        }
        return snapshots;
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

    public synchronized GameOrchestrator gameOf(String sessionId) {
        return this.requireSession(sessionId).gameOrchestrator;
    }

    public synchronized PingPongConnection connectionOf(String sessionId) {
        return this.requireSession(sessionId).connection;
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
                state.lobbyId,
                state.gameOrchestrator,
                state.lastPongAt,
                System.currentTimeMillis() - state.lastPongAt > TIMEOUT_MILLIS
        );
    }

    private static class SessionState {
        private final PingPongConnection connection;
        private User user;
        private Integer lobbyId;
        private GameOrchestrator gameOrchestrator;
        private long lastPongAt;

        private SessionState(PingPongConnection connection, long lastPongAt) {
            this.connection = connection;
            this.lastPongAt = lastPongAt;
        }
    }

    public record SessionSnapshot(
            String sessionId,
            PingPongConnection connection,
            User user,
            Integer lobbyId,
            GameOrchestrator gameOrchestrator,
            long lastPongAt,
            boolean timedOut
    ) {
    }
}
