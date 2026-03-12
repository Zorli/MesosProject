package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.Player;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LobbyManager {
    private int nextLobbyId;
    private final Map<Integer, Lobby> lobbies;

    public LobbyManager() {
        this.nextLobbyId = 1;
        this.lobbies = new HashMap<>();
    }

    public Lobby createLobby(Player host) {
        if (host == null) {
            throw new IllegalArgumentException("Host player cannot be null");
        }

        Lobby lobby = new Lobby(this.nextLobbyId++, host);
        this.lobbies.put(lobby.getId(), lobby);
        return lobby;
    }

    public void joinLobby(int lobbyId, Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }

        Lobby lobby = this.resolveLobby(lobbyId);
        this.ensureLobbyAcceptsPlayers(lobby);
        lobby.addPlayer(player);

        if (lobby.canStart() && lobby.getStatus() == LobbyStatus.WAITING) {
            lobby.setStatus(LobbyStatus.READY);
        }
    }

    public void exitLobby(int lobbyId, Player player) {
        this.removePlayer(lobbyId, player);
    }

    public void removePlayer(int lobbyId, Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }

        Lobby lobby = this.resolveLobby(lobbyId);
        boolean hostIsLeaving = lobby.isHost(player);
        lobby.removePlayer(player);

        if (lobby.getPlayers().isEmpty()) {
            lobby.setStatus(LobbyStatus.CLOSED);
            this.lobbies.remove(lobbyId);
            return;
        }

        if (hostIsLeaving) {
            lobby.transferHostToSecondPlayer();
        }

        if (!lobby.canStart() && lobby.getStatus() == LobbyStatus.READY) {
            lobby.setStatus(LobbyStatus.WAITING);
        }
    }

    public boolean canStart(int lobbyId) {
        Lobby lobby = this.resolveLobby(lobbyId);
        return lobby.canStart();
    }

    public List<Lobby> listOpenLobbies() {
        return this.lobbies.values().stream()
                .filter(lobby -> lobby.getStatus() != LobbyStatus.CLOSED)
                .filter(lobby -> lobby.getStatus() != LobbyStatus.STARTED)
                .sorted(Comparator.comparingInt(Lobby::getId))
                .toList();
    }

    public Optional<Lobby> getLobby(int lobbyId) {
        return Optional.ofNullable(this.lobbies.get(lobbyId));
    }

    // TODO: usare il tipo di ritorno `Game` e rimuovere la reflection quando la classe Game sarà disponibile in questo branch.
    public Object createGameFromLobby(int lobbyId) {
        Lobby lobby = this.resolveLobby(lobbyId);

        if (!lobby.canStart()) {
            throw new IllegalStateException("Cannot create game from lobby that cannot start");
        }

        try {
            Class<?> gameClass = Class.forName("it.polimi.ingsoft.mesos.server.model.GameState.Game");
            Constructor<?> gameConstructor = gameClass.getConstructor(int.class, ArrayList.class);

            Object game = gameConstructor.newInstance(lobby.getId(), new ArrayList<>(lobby.getPlayers()));
            lobby.setStatus(LobbyStatus.STARTED);
            return game;
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Game class is not available in this module yet", exception);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("Expected Game constructor (int, ArrayList<Player>) not found", exception);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Cannot instantiate Game from lobby", exception);
        }
    }

    private Lobby resolveLobby(int lobbyId) {
        Lobby lobby = this.lobbies.get(lobbyId);

        if (lobby == null) {
            throw new IllegalArgumentException("Lobby not found: " + lobbyId);
        }

        return lobby;
    }

    private void ensureLobbyAcceptsPlayers(Lobby lobby) {
        if (lobby.getStatus() == LobbyStatus.CLOSED || lobby.getStatus() == LobbyStatus.STARTED) {
            throw new IllegalStateException("Cannot join lobby with status: " + lobby.getStatus());
        }
    }

    @Override
    public String toString() {
        List<Lobby> orderedLobbies = this.lobbies.values().stream()
                .sorted(Comparator.comparingInt(Lobby::getId))
                .toList();

        return "LobbyManager{" +
                "nextLobbyId=" + this.nextLobbyId +
                ", lobbiesCount=" + this.lobbies.size() +
                ", lobbies=" + orderedLobbies +
                '}';
    }
}
