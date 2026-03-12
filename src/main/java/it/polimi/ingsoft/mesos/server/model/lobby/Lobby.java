package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.Player;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;

import java.util.ArrayList;
import java.util.List;

public class Lobby {
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 5;

    private final int id;
    private String hostNickname;
    private final List<Player> players;
    private LobbyStatus status;

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

    public int getId() {
        return this.id;
    }

    public String getHostNickname() {
        return this.hostNickname;
    }

    public int getMinPlayers() {
        return MIN_PLAYERS;
    }

    public int getMaxPlayers() {
        return MAX_PLAYERS;
    }

    public LobbyStatus getStatus() {
        return this.status;
    }

    public void setStatus(LobbyStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Lobby status cannot be null");
        }

        this.status = status;
    }

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

    public void removePlayer(Player player) {
        String nickname = this.resolveNickname(player);

        Player playerToRemove = this.players.stream()
                .filter(currentPlayer -> nickname.equals(currentPlayer.getNickname()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Player not present in lobby: " + nickname));

        this.players.remove(playerToRemove);
    }

    public boolean isHost(Player player) {
        if (player == null) {
            return false;
        }

        return this.hostNickname.equals(player.getNickname());
    }

    public void transferHostToSecondPlayer() {
        if (this.players.isEmpty()) {
            throw new IllegalStateException("Cannot assign host in an empty lobby");
        }

        this.hostNickname = this.resolveNickname(this.players.get(0));
    }

    public boolean canStart() {
        return this.players.size() >= MIN_PLAYERS;
    }

    public boolean isFull() {
        return this.players.size() >= MAX_PLAYERS;
    }

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
