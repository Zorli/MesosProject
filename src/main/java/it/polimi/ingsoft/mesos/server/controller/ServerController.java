package it.polimi.ingsoft.mesos.server.controller;
//This should be the pre match orchestrator

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
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
    private final UserManager userManager;
    private final LobbyManager lobbyManager;
    private final ScheduledExecutorService matchSetupScheduler;
    //mappa necessaria nello switch tra lobby e game,campi lobbyId, Gameorchestrator
    final Map<Integer, GameOrchestrator> activeGames = new ConcurrentHashMap<>();

    public ServerController() {
        this(new UserManager(), new LobbyManager());
    }

    public ServerController(UserManager userManager) {
        this(userManager, new LobbyManager());
    }

    public ServerController(UserManager userManager, LobbyManager lobbyManager) {
        this.userManager = Objects.requireNonNull(userManager, "userManager cannot be null");
        this.lobbyManager = Objects.requireNonNull(lobbyManager, "lobbyManager cannot be null");
        this.matchSetupScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "match-setup-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    public User authenticate(String nickname) {
        return this.userManager.registerUser(nickname);
    }

    public Lobby createLobby(User host, int chosenCapacity) {
        return this.lobbyManager.createLobby(host, chosenCapacity);
    }

    public void joinLobby(int lobbyId, User user) {
        this.lobbyManager.joinLobby(lobbyId, user);
    }

    public void exitLobby(int lobbyId, User user) {
        this.lobbyManager.exitLobby(lobbyId, user);
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

    public UserManager getUserManager() {
        return this.userManager;
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
