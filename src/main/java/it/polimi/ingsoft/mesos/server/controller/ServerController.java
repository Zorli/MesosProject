package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.controller.Factory.LobbyPlayerFactory;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.lobby.LobbyRegistry;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.NoOpSavedMatchService;
import it.polimi.ingsoft.mesos.server.model.user.User;
import it.polimi.ingsoft.mesos.server.network.PingPongConnection;
import it.polimi.ingsoft.mesos.server.network.PingPongRoutine;

import java.util.ArrayList;
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
    private final LobbyRegistry lobbyRegistry;
    private final GameFactory gameFactory;
    private final LobbyPlayerFactory lobbyPlayerFactory;
    private final PreMatchPayloadMapper payloadMapper;
    private final ScheduledExecutorService matchSetupScheduler;
    private final PingPongRoutine pingPongRoutine;
    final Map<Integer, GameOrchestrator> activeGames = new ConcurrentHashMap<>();

    public ServerController() {
        this(new LobbyRegistry(), DefaultGameFactory.getInstance());
    }

    public ServerController(GameFactory gameFactory) {
        this(new LobbyRegistry(), gameFactory);
    }

    public ServerController(LobbyRegistry lobbyRegistry, GameFactory gameFactory) {
        this.sessions = new ServerSessionRegistry();
        this.lobbyRegistry = Objects.requireNonNull(lobbyRegistry, "lobbyRegistry cannot be null");
        this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory cannot be null");
        this.lobbyPlayerFactory = new LobbyPlayerFactory();
        this.payloadMapper = new PreMatchPayloadMapper();
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
            this.exitLobby(sessionId);
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

    public String nicknameOf(String sessionId) {
        return this.sessions.nicknameOf(sessionId);
    }

    public Integer lobbyIdOf(String sessionId) {
        return this.sessions.lobbyIdOf(sessionId);
    }

    public GameOrchestrator gameOf(String sessionId) {
        return this.sessions.gameOf(sessionId);
    }

    public Lobby createLobby(String sessionId, int chosenCapacity) {
        this.ensureSessionCanEnterLobby(sessionId);
        String nickname = this.requireNickname(sessionId);

        Lobby lobby = this.lobbyRegistry.create(nickname, chosenCapacity);
        this.sessions.enterLobby(sessionId, lobby.getId());
        this.notifyLobbySessions(lobby.getId(), this.payloadMapper.lobbyCreated(nickname, lobby));
        return lobby;
    }

    public void joinLobby(String sessionId, int lobbyId) {
        this.ensureSessionCanEnterLobby(sessionId);
        String nickname = this.requireNickname(sessionId);
        Lobby lobby = this.lobbyRegistry.require(lobbyId);
        this.ensureLobbyAcceptsJoin(lobby);

        lobby.addPlayer(nickname);
        if (lobby.canStart()) {
            lobby.setStatus(LobbyStatus.READY);
        }

        this.sessions.enterLobby(sessionId, lobbyId);
        this.notifyLobbySessions(lobbyId, this.payloadMapper.lobbyJoined(nickname, lobby));
    }

    public void exitLobby(String sessionId) {
        Integer lobbyId = this.sessions.lobbyIdOf(sessionId);
        if (lobbyId == null) {
            throw new IllegalStateException("Client is not in a lobby");
        }

        String nickname = this.requireNickname(sessionId);
        Lobby lobby = this.lobbyRegistry.require(lobbyId);
        lobby.removePlayer(nickname);

        if (lobby.isEmpty()) {
            lobby.setStatus(LobbyStatus.CLOSED);
            this.lobbyRegistry.remove(lobbyId);
            this.notifyLobbySessions(lobbyId, this.payloadMapper.lobbyLeft(nickname, null, lobbyId));
        } else {
            if (!lobby.canStart() && lobby.getStatus() == LobbyStatus.READY) {
                lobby.setStatus(LobbyStatus.WAITING);
            }
            this.notifyLobbySessions(lobbyId, this.payloadMapper.lobbyLeft(nickname, lobby, lobbyId));
        }

        this.sessions.leaveLobby(sessionId);
    }

    public List<Lobby> listOpenLobbies() {
        return this.lobbyRegistry.listOpen();
    }

    public Optional<Lobby> getLobby(int lobbyId) {
        return this.lobbyRegistry.get(lobbyId);
    }

    public GameOrchestrator startLobby(String sessionId) {
        Integer lobbyId = this.sessions.lobbyIdOf(sessionId);
        if (lobbyId == null) {
            throw new IllegalStateException("Client is not in a lobby");
        }

        String nickname = this.requireNickname(sessionId);
        Lobby lobby = this.lobbyRegistry.require(lobbyId);
        this.ensureLobbyCanStart(lobby, nickname);

        List<Player> players = this.lobbyPlayerFactory.createPlayers(lobby.getPlayers());
        GameOrchestrator orchestrator = new GameOrchestrator(
                this.gameFactory,
                new ArrayList<>(players),
                lobby.getHostNickname(),
                NoOpSavedMatchService.INSTANCE
        );

        lobby.setStatus(LobbyStatus.STARTED);
        this.activeGames.put(lobbyId, orchestrator);
        this.addLobbySessionsAsGameListeners(lobbyId, orchestrator);
        this.enterLobbySessionsInGame(lobbyId, orchestrator);
        this.notifyLobbySessions(lobbyId, this.payloadMapper.lobbyStarted(nickname, lobby));
        this.scheduleMatchSetup(orchestrator);
        return orchestrator;
    }

    public void enterGameIfAvailable(String sessionId, int lobbyId) {
        this.sessions.enterGame(sessionId, this.getGameOrchestrator(lobbyId));
    }

    public GameOrchestrator getGameOrchestrator(int lobbyId) {
        GameOrchestrator orchestrator = this.activeGames.get(lobbyId);
        if (orchestrator == null) {
            throw new IllegalStateException("No Match open for lobby:: " + lobbyId);
        }
        return orchestrator;
    }

    private void ensureSessionCanEnterLobby(String sessionId) {
        if (this.sessions.lobbyIdOf(sessionId) != null) {
            throw new IllegalStateException("Client is already in a lobby");
        }
        String nickname = this.requireNickname(sessionId);
        if (this.lobbyRegistry.containsPlayer(nickname)) {
            throw new IllegalStateException("Client is already in a lobby");
        }
    }

    private void ensureLobbyAcceptsJoin(Lobby lobby) {
        if (lobby.getStatus() == LobbyStatus.CLOSED || lobby.getStatus() == LobbyStatus.STARTED) {
            throw new IllegalStateException("Cannot join lobby with status: " + lobby.getStatus());
        }
        if (lobby.isFull()) {
            throw new IllegalStateException("Lobby is full");
        }
    }

    private void ensureLobbyCanStart(Lobby lobby, String nickname) {
        if (!lobby.isHost(nickname)) {
            throw new IllegalStateException("Only the lobby host can start the match");
        }
        if (lobby.getStatus() != LobbyStatus.READY) {
            throw new IllegalStateException("Cannot start lobby with status: " + lobby.getStatus());
        }
        if (!lobby.canStart()) {
            throw new IllegalStateException("Cannot start lobby that cannot start");
        }
    }

    private String requireNickname(String sessionId) {
        String nickname = this.sessions.nicknameOf(sessionId);
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalStateException("Client is not authenticated");
        }
        return nickname;
    }

    private void enterLobbySessionsInGame(int lobbyId, GameOrchestrator orchestrator) {
        for (ServerSessionRegistry.SessionSnapshot session : this.sessions.sessionsInLobby(lobbyId)) {
            this.sessions.enterGame(session.sessionId(), orchestrator);
        }
    }

    private void addLobbySessionsAsGameListeners(int lobbyId, GameOrchestrator orchestrator) {
        for (ServerSessionRegistry.SessionSnapshot session : this.sessions.sessionsInLobby(lobbyId)) {
            if (session.connection() instanceof Listener listener) {
                orchestrator.getGame().addListener(listener);
            }
        }
    }

    private void notifyLobbySessions(int lobbyId, Object payload) {
        for (ServerSessionRegistry.SessionSnapshot session : this.sessions.sessionsInLobby(lobbyId)) {
            if (session.connection() instanceof Listener listener) {
                listener.notifyUpdate(payload);
            }
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
