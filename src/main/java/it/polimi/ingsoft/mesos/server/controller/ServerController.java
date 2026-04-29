package it.polimi.ingsoft.mesos.server.controller;
//This should be the pre match orchestrator

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.network.PingPongConnection;
import it.polimi.ingsoft.mesos.server.network.PingPongRoutine;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.lobby.LobbyManager;
import it.polimi.ingsoft.mesos.server.model.user.User;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerController {
    private static final int SECONDS_TO_WAIT = 5;
    private final ServerSessionRegistry sessions;
    private final LobbyManager lobbyManager;
    private final ScheduledExecutorService matchSetupScheduler;
    private final PingPongRoutine pingPongRoutine;
    //mappa necessaria nello switch tra lobby e game,campi lobbyId, Gameorchestrator
    final Map<Integer, GameOrchestrator> activeGames = new ConcurrentHashMap<>();

    public ServerController() {
        this(new LobbyManager());
    }

    public ServerController(LobbyManager lobbyManager) {
        this.sessions = new ServerSessionRegistry();
        this.lobbyManager = Objects.requireNonNull(lobbyManager, "lobbyManager cannot be null");
        this.matchSetupScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "match-setup-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        this.pingPongRoutine = new PingPongRoutine(this.sessions, this::handleConnectionTimeout);
        this.pingPongRoutine.start();
    }

    public String registerConnection(PingPongConnection connection) {
        return this.sessions.registerConnection(connection);
    }

    public void disconnectSession(String sessionId) {
        ServerSessionRegistry.SessionSnapshot disconnectedSession;
        try {
            disconnectedSession = this.sessions.snapshotOf(sessionId);
        } catch (RuntimeException ignored) {
            return;
        }

        if (disconnectedSession.gameOrchestrator() != null) {
            this.closeMatchForDisconnectedPlayer(disconnectedSession);
        } else if (disconnectedSession.user() != null && disconnectedSession.lobbyId() != null) {
            this.lobbyManager.exitLobby(disconnectedSession.lobbyId(), disconnectedSession.user());
        }
        this.sessions.unregister(sessionId);
    }

    public void recordPong(String sessionId) {
        this.sessions.recordPong(sessionId);
    }

    public boolean isAuthenticated(String sessionId) {
        return this.sessions.isAuthenticated(sessionId);
    }

    public User authenticate(String sessionId, String nickname) {
        return this.sessions.authenticate(sessionId, nickname);
    }

    public User userOf(String sessionId) {
        return this.sessions.userOf(sessionId);
    }

    public String nicknameOf(String sessionId) {
        return this.sessions.nicknameOf(sessionId);
    }

    public Integer lobbyIdOf(String sessionId) {
        return this.sessions.lobbyIdOf(sessionId);
    }

    public GameOrchestrator gameOf(String sessionId) {
        return this.sessions.gameOf(sessionId);
    }

    public Lobby createLobby(User host, int chosenCapacity) {
        return this.lobbyManager.createLobby(host, chosenCapacity);
    }

    public Lobby createLobby(String sessionId, int chosenCapacity) {
        Lobby lobby = this.lobbyManager.createLobby(this.sessions.userOf(sessionId), chosenCapacity);
        this.sessions.enterLobby(sessionId, lobby.getId());
        return lobby;
    }

    public void joinLobby(int lobbyId, User user) {
        this.lobbyManager.joinLobby(lobbyId, user);
    }

    public void joinLobby(String sessionId, int lobbyId) {
        this.lobbyManager.joinLobby(lobbyId, this.sessions.userOf(sessionId));
        this.sessions.enterLobby(sessionId, lobbyId);
    }

    public void exitLobby(int lobbyId, User user) {
        this.lobbyManager.exitLobby(lobbyId, user);
    }

    public void exitLobby(String sessionId) {
        Integer lobbyId = this.sessions.lobbyIdOf(sessionId);
        if (lobbyId == null) {
            throw new IllegalStateException("Client is not in a lobby");
        }
        this.lobbyManager.exitLobby(lobbyId, this.sessions.userOf(sessionId));
        this.sessions.leaveLobby(sessionId);
    }

    public List<Lobby> listOpenLobbies() {
        return this.lobbyManager.listOpenLobbies();
    }

    public Optional<Lobby> getLobby(int lobbyId) {
        return this.lobbyManager.getLobby(lobbyId);
    }

    public GameOrchestrator createGameFromLobby(int lobbyId) {
        GameOrchestrator orchestrator = this.lobbyManager.createGameFromLobby(lobbyId);
        this.activeGames.put(lobbyId, orchestrator);
        this.scheduleMatchSetup(orchestrator);
        return orchestrator;
    }

    public GameOrchestrator createGameFromLobby(int lobbyId, User requester) {
        Lobby lobby = this.lobbyManager.getLobby(lobbyId)
                .orElseThrow(() -> new IllegalArgumentException("Lobby not found: " + lobbyId));
        if (!lobby.isHost(requester)) {
            throw new IllegalStateException("Only the lobby host can start the match");
        }
        return this.createGameFromLobby(lobbyId);
    }

    public GameOrchestrator startLobby(String sessionId) {
        Integer lobbyId = this.sessions.lobbyIdOf(sessionId);
        if (lobbyId == null) {
            throw new IllegalStateException("Client is not in a lobby");
        }
        GameOrchestrator orchestrator = this.createGameFromLobby(lobbyId, this.sessions.userOf(sessionId));
        this.enterLobbySessionsInGame(lobbyId, orchestrator);
        return orchestrator;
    }

    public void enterGameIfAvailable(String sessionId, int lobbyId) {
        this.sessions.enterGame(sessionId, this.getGameOrchestrator(lobbyId));
    }

    public LobbyManager getLobbyManager() {
        return this.lobbyManager;
    }

    public GameOrchestrator getGameOrchestrator(int lobbyId) {
        GameOrchestrator orchestrator = this.activeGames.get(lobbyId);
        if (orchestrator == null) {
            throw new IllegalStateException("No Match open for lobby:: " + lobbyId);
        }
        return orchestrator;
    }

    private void enterLobbySessionsInGame(int lobbyId, GameOrchestrator orchestrator) {
        for (ServerSessionRegistry.SessionSnapshot session : this.sessions.sessionsInLobby(lobbyId)) {
            this.sessions.enterGame(session.sessionId(), orchestrator);
        }
    }

    private void handleConnectionTimeout(String sessionId) {
        ServerSessionRegistry.SessionSnapshot disconnectedSession;
        try {
            disconnectedSession = this.sessions.snapshotOf(sessionId);
        } catch (RuntimeException ignored) {
            return;
        }

        try {
            this.closeConnection(disconnectedSession.connection());
            this.disconnectSession(sessionId);
        } catch (RuntimeException ignored) {
        }
    }

    private void closeMatchForDisconnectedPlayer(ServerSessionRegistry.SessionSnapshot disconnectedSession) {
        GameOrchestrator interruptedOrchestrator = disconnectedSession.gameOrchestrator();
        Integer interruptedLobbyId = disconnectedSession.lobbyId();

        interruptedOrchestrator.freezeForDisconnectedPlayer(disconnectedSession.user().getNickname());
        if (interruptedLobbyId != null) {
            this.activeGames.remove(interruptedLobbyId);
        }

        for (ServerSessionRegistry.SessionSnapshot session : this.sessions.sessionsInGame(interruptedOrchestrator)) {
            this.sessions.leaveGame(session.sessionId());
            this.sessions.leaveLobby(session.sessionId());
        }
    }

    private void closeConnection(PingPongConnection connection) {
        try {
            connection.close();
        } catch (Exception ignored) {
        }
    }

    private void scheduleMatchSetup(GameOrchestrator orchestrator) {
        this.matchSetupScheduler.schedule(
                () -> {
                    if (orchestrator.getGame().getStatus() == GameStatus.SETUP) {
                        orchestrator.setupMatch();
                    }
                },
                SECONDS_TO_WAIT,
                TimeUnit.SECONDS
        );
    }
}
