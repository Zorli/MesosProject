package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal pre-match lobby state.
 */
public class Lobby {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 5;

    private final int id;
    private final int capacity;
    private final List<String> players;
    private String hostNickname;
    private LobbyStatus status;

    public Lobby(int id, String hostNickname, int capacity) {
        if (capacity < MIN_PLAYERS || capacity > MAX_PLAYERS) {
            throw new IllegalArgumentException("Capacity must be between " + MIN_PLAYERS + " and " + MAX_PLAYERS);
        }

        this.id = id;
        this.capacity = capacity;
        this.players = new ArrayList<>();
        this.hostNickname = validateNickname(hostNickname);
        this.status = LobbyStatus.WAITING;
        this.players.add(this.hostNickname);
    }

    public int getId() {
        return this.id;
    }

    public String getHostNickname() {
        return this.hostNickname;
    }

    public List<String> getPlayers() {
        return List.copyOf(this.players);
    }

    public int getChosenCapacity() {
        return this.capacity;
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

    public boolean hasPlayer(String nickname) {
        return this.players.contains(validateNickname(nickname));
    }

    public void addPlayer(String nickname) {
        String validatedNickname = validateNickname(nickname);
        if (this.players.contains(validatedNickname)) {
            throw new IllegalStateException("User already present in lobby: " + validatedNickname);
        }
        if (this.isFull()) {
            throw new IllegalStateException("Lobby is full");
        }

        this.players.add(validatedNickname);
    }

    public void removePlayer(String nickname) {
        String validatedNickname = validateNickname(nickname);
        boolean removed = this.players.remove(validatedNickname);
        if (!removed) {
            throw new IllegalStateException("User not present in lobby: " + validatedNickname);
        }

        if (validatedNickname.equals(this.hostNickname)) {
            this.hostNickname = this.players.isEmpty() ? null : this.players.get(0);
        }
    }

    public boolean isHost(String nickname) {
        return this.hostNickname != null && this.hostNickname.equals(nickname);
    }

    public boolean canStart() {
        return this.players.size() == this.capacity;
    }

    public boolean isFull() {
        return this.players.size() >= this.capacity;
    }

    public boolean isEmpty() {
        return this.players.isEmpty();
    }

    private static String validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("User nickname cannot be null or blank");
        }

        return nickname;
    }

    @Override
    public String toString() {
        return "Lobby{" +
                "id=" + this.id +
                ", hostNickname='" + this.hostNickname + '\'' +
                ", status=" + this.status +
                ", players=" + this.players +
                ", capacity=" + this.capacity +
                '}';
    }
}
