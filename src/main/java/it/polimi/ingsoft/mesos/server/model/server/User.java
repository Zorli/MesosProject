package it.polimi.ingsoft.mesos.server.model.server;

import java.util.Objects;

/**
 * Represents a user connected to the server.
 * Connection ownership is tracked by the server session registry, not by this class.
 */
public class User {
    private final String nickname;

    /**
     * Creates a new user with the specified nickname.
     *
     * @param nickname the unique identifier chosen by the user
     * @throws IllegalArgumentException if the provided nickname is null or blank
     */
    public User(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("nickname can't be null or blank");
        }
        this.nickname = nickname;
    }

    /**
     * Retrieves the nickname of the user.
     *
     * @return the user's nickname
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * Compares this user to the specified object. The result is {@code true} if and only if
     * the argument is not null and is a User object that has the exact same nickname as this object.
     *
     * @param o the object to compare this User against
     * @return {@code true} if the given object represents a User with the same nickname, {@code false} otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return nickname.equals(user.nickname);
    }

    /**
     * Returns a hash code for this user, which is computed exclusively based on the nickname.
     *
     * @return a hash code value for this object
     */
    @Override
    public int hashCode() {
        return Objects.hash(nickname);
    }
}
