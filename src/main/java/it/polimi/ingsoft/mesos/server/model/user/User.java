package it.polimi.ingsoft.mesos.server.model.user;

import java.util.Objects;

/**
 * Represents a globally recognized user connected to the server.
 * This class handles the virtualview identity and connection status of a client,
 * keeping it strictly separated from the in-game player state.
 */
public class User {
    private final String nickname;
    private boolean isOnline;

    /**
     * Creates a new user with the specified nickname and sets their initial status to online.
     *
     * @param nickname the unique identifier chosen by the user
     * @throws IllegalArgumentException if the provided nickname is null or blank
     */
    public User(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("nickname can't be null or blank");
        }
        this.nickname = nickname;
        this.isOnline = true;
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
     * Checks whether the user is currently connected to the server.
     *
     * @return {@code true} if the user is online, {@code false} otherwise
     */
    public boolean isOnline() {
        return isOnline;
    }

    /**
     * Updates the connection status of the user.
     *
     * @param online {@code true} to mark the user as connected, {@code false} for disconnected
     */
    public void setOnline(boolean online) {
        this.isOnline = online;
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