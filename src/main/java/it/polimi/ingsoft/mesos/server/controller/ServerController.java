package it.polimi.ingsoft.mesos.server.controller;
//This should be the pre match orchestrator

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.lobby.LobbyManager;
import it.polimi.ingsoft.mesos.server.model.user.User;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ServerController {
    private final UserManager userManager;
    private final LobbyManager lobbyManager;
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
        return orchestrator;
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
}
