package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.preLobby.User;

/**
 * Small factory of lobby test users used to keep lobby-focused tests concise.
 */
final class LobbyTestUsers {
    /**
     * Prevents instantiation of the test helper class.
     */
    private LobbyTestUsers() {
    }

    /**
     * Creates a user with the provided nickname.
     *
     * @param nickname nickname assigned to the generated user
     * @return user ready to be used in lobby test scenarios
     */
    static User userWithNickname(String nickname) {
        return new User(nickname);
    }
}
