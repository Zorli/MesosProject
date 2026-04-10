package it.polimi.ingsoft.mesos.server.controller;
//This should be the pre match orchestrator

import it.polimi.ingsoft.mesos.server.model.user.User;

import java.util.Objects;

public class ServerController {
    private final UserManager userManager;

    public ServerController() {
        this.userManager = new UserManager();
    }

    public ServerController(UserManager userManager) {
        this.userManager = Objects.requireNonNull(userManager, "userManager cannot be null");
    }

    public User authenticate(String nickname) {
        return this.userManager.registerUser(nickname);
    }

    public UserManager getUserManager() {
        return this.userManager;
    }
}
