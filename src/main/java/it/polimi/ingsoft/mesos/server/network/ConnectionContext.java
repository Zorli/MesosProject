package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.model.user.User;

import java.util.Objects;

public class ConnectionContext {
    public enum ConnectionPhase {
        AUTHENTICATION,
        PRE_MATCH,
        IN_MATCH
    }

    private final ServerController serverController;
    private GameOrchestrator gameOrchestrator;
    private User user;
    private String nickname;
    private ConnectionPhase phase;

    public ConnectionContext(ServerController serverController) {
        this.serverController = Objects.requireNonNull(serverController, "serverController cannot be null");
        this.phase = ConnectionPhase.AUTHENTICATION;
    }

    public ServerController getServerController() {
        return this.serverController;
    }

    public GameOrchestrator getGameOrchestrator() {
        return this.gameOrchestrator;
    }

    public void setGameOrchestrator(GameOrchestrator gameOrchestrator) {
        this.gameOrchestrator = gameOrchestrator;
        this.phase = gameOrchestrator == null ? ConnectionPhase.PRE_MATCH : ConnectionPhase.IN_MATCH;
    }

    public boolean isInMatch() {
        return this.phase == ConnectionPhase.IN_MATCH;
    }

    public String getNickname() {
        if (this.user != null) {
            return this.user.getNickname();
        }
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
        if (user == null) {
            this.nickname = null;
            this.phase = ConnectionPhase.AUTHENTICATION;
            return;
        }

        this.nickname = user.getNickname();
        if (this.phase == ConnectionPhase.AUTHENTICATION) {
            this.phase = ConnectionPhase.PRE_MATCH;
        }
    }

    public boolean isAuthenticated() {
        return this.phase != ConnectionPhase.AUTHENTICATION && this.user != null;
    }

    public ConnectionPhase getPhase() {
        return this.phase;
    }

    public boolean isInAuthenticationPhase() {
        return this.phase == ConnectionPhase.AUTHENTICATION;
    }

    public boolean isInPreMatchPhase() {
        return this.phase == ConnectionPhase.PRE_MATCH;
    }

    public void authenticate(User user) {
        this.setUser(Objects.requireNonNull(user, "user cannot be null"));
        this.phase = ConnectionPhase.PRE_MATCH;
    }

    public void enterMatch(GameOrchestrator gameOrchestrator) {
        this.setGameOrchestrator(Objects.requireNonNull(gameOrchestrator, "gameOrchestrator cannot be null"));
    }

    public void leaveMatch() {
        this.gameOrchestrator = null;
        this.phase = this.user == null ? ConnectionPhase.AUTHENTICATION : ConnectionPhase.PRE_MATCH;
    }
}
