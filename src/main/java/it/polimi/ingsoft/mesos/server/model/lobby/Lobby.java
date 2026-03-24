package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.controller.player.Player;

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
    private final String resumableGameId;
    private final List<String> reservedPlayerNicknames;
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
        this(id, host, null, List.of(), LobbyStatus.WAITING);
    }

    /**
     * Creates a resumable lobby for a suspended match. No player is connected yet.
     *
     * @param id unique lobby identifier
     * @param resumableGameId persisted match identifier
     * @param reservedPlayerNicknames nicknames allowed to rejoin this suspended match
     * @return lobby waiting for the original players to reconnect
     */
    public static Lobby resumable(int id, String resumableGameId, List<String> reservedPlayerNicknames) {
        if (resumableGameId == null || resumableGameId.isBlank()) {
            throw new IllegalArgumentException("Resumable game id cannot be null or blank");
        }

        if (reservedPlayerNicknames == null || reservedPlayerNicknames.size() < MIN_PLAYERS) {
            throw new IllegalArgumentException("Reserved player nicknames must contain at least two players");
        }

        List<String> normalizedNicknames = reservedPlayerNicknames.stream()
                .map(Lobby::validateNickname)
                .toList();

        if (normalizedNicknames.stream().distinct().count() != normalizedNicknames.size()) {
            throw new IllegalArgumentException("Reserved player nicknames must be unique");
        }

        if (normalizedNicknames.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException("Reserved player nicknames exceed maximum lobby size");
        }

        return new Lobby(id, null, resumableGameId, normalizedNicknames, LobbyStatus.RESUMABLE);
    }

    /**
     * Internal constructor used by both standard and resumable lobbies.
     */
    private Lobby(int id,
                  Player host,
                  String resumableGameId,
                  List<String> reservedPlayerNicknames,
                  LobbyStatus status) {
        if (host == null && status != LobbyStatus.RESUMABLE) {
            throw new IllegalArgumentException("Host player cannot be null");
        }

        this.id = id;
        this.resumableGameId = resumableGameId;
        this.reservedPlayerNicknames = List.copyOf(reservedPlayerNicknames);
        this.hostNickname = host == null ? null : this.resolveNickname(host);
        this.players = new ArrayList<>();
        this.status = status;

        if (host != null) {
            this.addPlayer(host);
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
     * Returns the original player roster reserved for this resumable lobby.
     *
     * @return immutable list of reserved nicknames
     */
    public List<String> getReservedPlayerNicknames() {
        return this.reservedPlayerNicknames;
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

        if (this.isResumable() && !this.reservedPlayerNicknames.contains(nickname)) {
            throw new IllegalStateException("Player cannot join resumable lobby: " + nickname);
        }

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

        if (this.hostNickname == null) {
            this.hostNickname = nickname;
        }
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
        if (this.isResumable()) {
            return this.players.size() == this.reservedPlayerNicknames.size();
        }

        return this.players.size() >= MIN_PLAYERS;
    }

    /**
     * Checks whether the lobby has reached maximum capacity.
     *
     * @return {@code true} if the lobby is full, {@code false} otherwise
     */
    public boolean isFull() {
        if (this.isResumable()) {
            return this.players.size() >= this.reservedPlayerNicknames.size();
        }

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

    /**
     * Extracts and validates the nickname of a player used in lobby membership checks.
     *
     * @param player player whose nickname must be resolved
     * @return validated non-blank nickname
     */
    private String resolveNickname(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }

        return validateNickname(player.getNickname());
    }

    /**
     * Validates and returns one nickname string.
     */
    private static String validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("Player nickname cannot be null or blank");
        }

        return nickname;
    }

    /**
     * Returns a debug-friendly textual representation of the lobby.
     *
     * @return string containing the main lobby fields and player nicknames
     */
    @Override
    public String toString() {
        List<String> playerNicknames = this.players.stream()
                .map(Player::getNickname)
                .toList();

        return "Lobby{" +
                "id=" + this.id +
                ", resumableGameId='" + this.resumableGameId + '\'' +
                ", hostNickname='" + this.hostNickname + '\'' +
                ", status=" + this.status +
                ", playersCount=" + this.players.size() +
                ", maxPlayers=" + MAX_PLAYERS +
                ", reservedPlayerNicknames=" + this.reservedPlayerNicknames +
                ", playerNicknames=" + playerNicknames +
                '}';
    }
}
