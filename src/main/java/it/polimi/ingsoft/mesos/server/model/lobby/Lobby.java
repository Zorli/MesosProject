package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.Player;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a pre-game lobby where players gather before a match starts.
 * A lobby keeps track of its host, participating players and current lifecycle status.
 */
public class Lobby {
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 5;

    private final int id;
    private String hostNickname;
    private final List<Player> players;
    private LobbyStatus status;

    /**
     * Creates a new lobby and assigns the creator as first player and host.
     *
     * @param id unique lobby identifier
     * @param host player creating the lobby
     */
    public Lobby(int id, Player host) {
        if (host == null) {
            throw new IllegalArgumentException("Host player cannot be null");
        }

        this.id = id;
        this.hostNickname = this.resolveNickname(host);
        this.players = new ArrayList<>();
        this.status = LobbyStatus.WAITING;
        this.addPlayer(host);
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
     * Returns the minimum number of players required to start a game.
     *
     * @return minimum number of players
     */
    public int getMinPlayers() {
        return MIN_PLAYERS;
    }

    /**
     * Returns the maximum number of players allowed in the lobby.
     *
     * @return maximum number of players
     */
    public int getMaxPlayers() {
        return MAX_PLAYERS;
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
     * Adds a player to the lobby if there is space and the player is not already present.
     *
     * @param player player to add
     */
    public void addPlayer(Player player) {
        String nickname = this.resolveNickname(player);

        if (this.isFull()) {
            throw new IllegalStateException("Lobby is full");
        }

        boolean playerAlreadyInLobby = this.players.stream()
                .map(Player::getNickname)
                .anyMatch(nickname::equals);

        if (playerAlreadyInLobby) {
            throw new IllegalStateException("Player already present in lobby: " + nickname);
        }

        this.players.add(player);
    }

    /**
     * Removes a player from the lobby.
     *
     * @param player player to remove
     */
    public void removePlayer(Player player) {
        String nickname = this.resolveNickname(player);

        Player playerToRemove = this.players.stream()
                .filter(currentPlayer -> nickname.equals(currentPlayer.getNickname()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Player not present in lobby: " + nickname));

        boolean removingHost = nickname.equals(this.hostNickname);
        this.players.remove(playerToRemove);

        if (removingHost) {
            if (this.players.isEmpty()) {
                this.hostNickname = null;
            } else {
                this.transferHostToSecondPlayer();
            }
        }
    }

    /**
     * Checks whether the given player is the current host.
     *
     * @param player player to check
     * @return {@code true} if the player is the host, {@code false} otherwise
     */
    public boolean isHost(Player player) {
        if (player == null || this.hostNickname == null) {
            return false;
        }

        return this.hostNickname.equals(player.getNickname());
    }

    /**
     * Transfers host ownership to the first remaining player in the lobby.
     */
    public void transferHostToSecondPlayer() {
        if (this.players.isEmpty()) {
            throw new IllegalStateException("Cannot assign host in an empty lobby");
        }

        this.hostNickname = this.resolveNickname(this.players.get(0));
    }

    /**
     * Checks whether the lobby has enough players to start a game.
     *
     * @return {@code true} if the lobby can start, {@code false} otherwise
     */
    public boolean canStart() {
        return this.players.size() >= MIN_PLAYERS;
    }

    /**
     * Checks whether the lobby has reached maximum capacity.
     *
     * @return {@code true} if the lobby is full, {@code false} otherwise
     */
    public boolean isFull() {
        return this.players.size() >= MAX_PLAYERS;
    }

    /**
     * Returns an immutable snapshot of the players currently in the lobby.
     *
     * @return immutable player list
     */
    public List<Player> getPlayers() {
        return List.copyOf(this.players);
    }

    private String resolveNickname(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }

        String nickname = player.getNickname();

        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("Player nickname cannot be null or blank");
        }

        return nickname;
    }

    @Override
    public String toString() {
        List<String> playerNicknames = this.players.stream()
                .map(Player::getNickname)
                .toList();

        return "Lobby{" +
                "id=" + this.id +
                ", hostNickname='" + this.hostNickname + '\'' +
                ", status=" + this.status +
                ", playersCount=" + this.players.size() +
                ", maxPlayers=" + MAX_PLAYERS +
                ", playerNicknames=" + playerNicknames +
                '}';
    }
}
