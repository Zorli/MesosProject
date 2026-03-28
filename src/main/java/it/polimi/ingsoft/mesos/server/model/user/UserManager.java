package it.polimi.ingsoft.mesos.server.model.user;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages the registry of all users currently registered or connected to the server.
 * This class acts as the single source of truth for user network identities,
 * ensuring that all connected users have unique nicknames.
 */
public class UserManager {

    private final Map<String, User> usersByNickname;

    /**
     * Creates a new, empty user manager.
     * Initializes a thread-safe registry to safely handle concurrent connections.
     */
    public UserManager() {
        this.usersByNickname = new ConcurrentHashMap<>();
    }

    /**
     * Attempts to register a new user with the specified nickname.
     * * @param nickname the unique nickname chosen by the user
     * @return the newly created {@code User} object
     * @throws IllegalArgumentException if the chosen nickname is already taken by another user
     */
    public User registerUser(String nickname) {
        if (usersByNickname.containsKey(nickname)) {
            throw new IllegalArgumentException("Nickname already taken: " + nickname);
        }

        User newUser = new User(nickname);
        usersByNickname.put(newUser.getNickname(), newUser);

        return newUser;
    }

    /**
     * Retrieves an existing user by their nickname.
     * * @param nickname the nickname of the user to search for
     * @return an {@code Optional} containing the user if found, or an empty {@code Optional} otherwise
     */
    public Optional<User> getUserByNickname(String nickname) {
        return Optional.ofNullable(usersByNickname.get(nickname));
    }

}