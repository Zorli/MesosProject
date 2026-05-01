package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores active lobbies and assigns lobby identifiers.
 */
public class LobbyRegistry {
    private int nextLobbyId = 1;
    private final Map<Integer, Lobby> lobbies = new HashMap<>();

    public Lobby create(String hostNickname, int capacity) {
        Lobby lobby = new Lobby(this.nextLobbyId++, hostNickname, capacity);
        this.lobbies.put(lobby.getId(), lobby);
        return lobby;
    }

    public Optional<Lobby> get(int lobbyId) {
        return Optional.ofNullable(this.lobbies.get(lobbyId));
    }

    public Lobby require(int lobbyId) {
        return this.get(lobbyId)
                .orElseThrow(() -> new IllegalArgumentException("Lobby not found: " + lobbyId));
    }

    public void remove(int lobbyId) {
        this.lobbies.remove(lobbyId);
    }

    public List<Lobby> listOpen() {
        return this.lobbies.values().stream()
                .filter(lobby -> lobby.getStatus() != LobbyStatus.CLOSED)
                .filter(lobby -> lobby.getStatus() != LobbyStatus.STARTED)
                .sorted(Comparator.comparingInt(Lobby::getId))
                .toList();
    }

    public boolean containsPlayer(String nickname) {
        return this.lobbies.values().stream()
                .filter(lobby -> lobby.getStatus() != LobbyStatus.CLOSED)
                .filter(lobby -> lobby.getStatus() != LobbyStatus.STARTED)
                .anyMatch(lobby -> lobby.hasPlayer(nickname));
    }
}
