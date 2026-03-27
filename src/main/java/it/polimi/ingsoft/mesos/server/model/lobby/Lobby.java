package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.user.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a pre-game lobby where users gather before a match starts.
 * A lobby keeps track of its host, participating users, chosen capacity, and current lifecycle status.
 */
public class Lobby {
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 5;

    private final int id;
    private final String resumableGameId;
    private final List<String> reservedNicknames;
    private final int chosenCapacity;
    private String hostNickname;
    private final List<User> users;
    private LobbyStatus status;

    /**
     * Creates a new lobby and assigns the creator as first user and host.
     *
     * @param id unique lobby identifier
     * @param host user creating the lobby
     * @param chosenCapacity number of users chosen by the host to start the game
     */
    public Lobby(int id, User host, int chosenCapacity) {
        this(id, host, null, List.of(), LobbyStatus.WAITING, chosenCapacity);
    }

    /**
     * Creates a resumable lobby for a suspended match. No user is connected yet.
     *
     * @param id unique lobby identifier
     * @param resumableGameId persisted match identifier
     * @param reservedNicknames nicknames allowed to rejoin this suspended match
     * @return lobby waiting for the original users to reconnect
     */
    public static Lobby resumable(int id, String resumableGameId, List<String> reservedNicknames) {
        if (resumableGameId == null || resumableGameId.isBlank()) {
            throw new IllegalArgumentException("Resumable game id cannot be null or blank");
        }

        if (reservedNicknames == null || reservedNicknames.size() < MIN_PLAYERS) {
            throw new IllegalArgumentException("Reserved nicknames must contain at least two players");
        }

        if (reservedNicknames.stream().distinct().count() != reservedNicknames.size()) {
            throw new IllegalArgumentException("Reserved nicknames must be unique");
        }

        if (reservedNicknames.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException("Reserved nicknames exceed maximum lobby size");
        }

        int capacity = reservedNicknames.size();
        return new Lobby(id, null, resumableGameId, reservedNicknames, LobbyStatus.RESUMABLE, capacity);
    }

    /**
     * Internal constructor used by both standard and resumable lobbies.
     */
    private Lobby(int id,
                  User host,
                  String resumableGameId,
                  List<String> reservedNicknames,
                  LobbyStatus status,
                  int chosenCapacity) {

        if (host == null && status != LobbyStatus.RESUMABLE) {
            throw new IllegalArgumentException("Host user cannot be null");
        }

        if (chosenCapacity < MIN_PLAYERS || chosenCapacity > MAX_PLAYERS) {
            throw new IllegalArgumentException("Capacity must be between " + MIN_PLAYERS + " and " + MAX_PLAYERS);
        }

        this.id = id;
        this.resumableGameId = resumableGameId;
        this.reservedNicknames = List.copyOf(reservedNicknames);
        this.chosenCapacity = chosenCapacity;
        this.hostNickname = host == null ? null : host.getNickname();
        this.users = new ArrayList<>();
        this.status = status;

        if (host != null) {
            this.addUser(host);
        }
    }

    /**
     * Returns the unique identifier of the lobby.
     *
     * @return lobby identifier
     */
    public int getId() {
        return this.id;
    }

    /**
     * Returns the nickname of the current host.
     *
     * @return host nickname
     */
    public String getHostNickname() {
        return this.hostNickname;
    }

    /**
     * Returns whether this lobby represents a suspended match to be resumed.
     *
     * @return {@code true} for resumable lobbies
     */
    public boolean isResumable() {
        return this.resumableGameId != null;
    }

    /**
     * Returns the persisted match id associated with a resumable lobby.
     *
     * @return resumable game id or {@code null} for standard lobbies
     */
    public String getResumableGameId() {
        return this.resumableGameId;
    }

    /**
     * Returns the original user roster reserved for this resumable lobby.
     *
     * @return immutable list of reserved nicknames
     */
    public List<String> getReservedNicknames() {
        return this.reservedNicknames;
    }

    /**
     * Returns the minimum number of users required to start a game.
     *
     * @return minimum number of users
     */
    public int getMinPlayers() {
        return MIN_PLAYERS;
    }

    /**
     * Returns the maximum number of users allowed in the lobby.
     *
     * @return maximum number of users
     */
    public int getMaxPlayers() {
        return MAX_PLAYERS;
    }

    /**
     * Returns the target capacity chosen for this lobby.
     *
     * @return chosen capacity
     */
    public int getChosenCapacity() {
        return this.chosenCapacity;
    }

    /**
     * Returns the current lobby status.
     *
     * @return current status
     */
    public LobbyStatus getStatus() {
        return this.status;
    }

    /**
     * Updates the lobby status.
     *
     * @param status new lobby status
     */
    void setStatus(LobbyStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Lobby status cannot be null");
        }

        this.status = status;
    }

    /**
     * Adds a user to the lobby if there is space and the user is not already present.
     *
     * @param user user to add
     */
    public void addUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        if (this.isResumable() && !this.reservedNicknames.contains(user.getNickname())) {
            throw new IllegalStateException("User cannot join resumable lobby: " + user.getNickname());
        }

        if (this.isFull()) {
            throw new IllegalStateException("Lobby is full");
        }

        if (this.users.contains(user)) {
            throw new IllegalStateException("User already present in lobby: " + user.getNickname());
        }

        this.users.add(user);

        if (this.hostNickname == null) {
            this.hostNickname = user.getNickname();
        }
    }

    /**
     * Removes a user from the lobby.
     *
     * @param user user to remove
     */
    public void removeUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        boolean wasRemoved = this.users.remove(user);

        if (!wasRemoved) {
            throw new IllegalStateException("User not present in lobby: " + user.getNickname());
        }

        boolean removingHost = user.getNickname().equals(this.hostNickname);

        if (removingHost) {
            if (this.users.isEmpty()) {
                this.hostNickname = null;
            } else {
                this.transferHostToSecondUser();
            }
        }
    }

    /**
     * Checks whether the given user is the current host.
     *
     * @param user user to check
     * @return {@code true} if the user is the host, {@code false} otherwise
     */
    public boolean isHost(User user) {
        if (user == null || this.hostNickname == null) {
            return false;
        }

        return this.hostNickname.equals(user.getNickname());
    }

    /**
     * Transfers host ownership to the first remaining user in the lobby.
     */
    public void transferHostToSecondUser() {
        if (this.users.isEmpty()) {
            throw new IllegalStateException("Cannot assign host in an empty lobby");
        }

        this.hostNickname = this.users.get(0).getNickname();
    }

    /**
     * Checks whether the lobby has reached the chosen capacity to start a game.
     *
     * @return {@code true} if the lobby can start, {@code false} otherwise
     */
    public boolean canStart() {
        return this.users.size() == this.chosenCapacity;
    }

    /**
     * Checks whether the lobby has reached its chosen capacity.
     *
     * @return {@code true} if the lobby is full, {@code false} otherwise
     */
    public boolean isFull() {
        return this.users.size() >= this.chosenCapacity;
    }

    /**
     * Returns an immutable snapshot of the users currently in the lobby.
     *
     * @return immutable user list
     */
    public List<User> getUsers() {
        return List.copyOf(this.users);
    }

    /**
     * Returns a debug-friendly textual representation of the lobby.
     *
     * @return string containing the main lobby fields and user nicknames
     */
    @Override
    public String toString() {
        List<String> userNicknames = this.users.stream()
                .map(User::getNickname)
                .toList();

        return "Lobby{" +
                "id=" + this.id +
                ", resumableGameId='" + this.resumableGameId + '\'' +
                ", hostNickname='" + this.hostNickname + '\'' +
                ", status=" + this.status +
                ", usersCount=" + this.users.size() +
                ", chosenCapacity=" + this.chosenCapacity +
                ", reservedNicknames=" + this.reservedNicknames +
                ", userNicknames=" + userNicknames +
                '}';
    }
}