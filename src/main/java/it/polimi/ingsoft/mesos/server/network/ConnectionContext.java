package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.model.user.User;

import java.util.Objects;

public class ConnectionContext {
    private final ServerController serverController;
    private GameOrchestrator gameOrchestrator;
    private User user;
    private String nickname;

    public ConnectionContext(ServerController serverController) {
        this.serverController = Objects.requireNonNull(serverController, "serverController cannot be null");
    }

    public ServerController getServerController() {
        return this.serverController;
    }

    public GameOrchestrator getGameOrchestrator() {
        return this.gameOrchestrator;
    }

    public void setGameOrchestrator(GameOrchestrator gameOrchestrator) {
        this.gameOrchestrator = gameOrchestrator;
    }

    public boolean isInMatch() {
        return this.gameOrchestrator != null;
    }

    public String getNickname() {
        return this.nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean isAuthenticated() {
        return this.user != null;
    }
}
