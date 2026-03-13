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

/**
 * Coordinates the lifecycle of all active lobbies.
 * It is responsible for creating lobbies, adding and removing players,
 * exposing open lobbies and starting a game from a ready lobby.
 */
public class LobbyManager {
    private int nextLobbyId;
    private final Map<Integer, Lobby> lobbies;

    /**
     * Creates an empty lobby manager with progressive lobby identifiers.
     */
    public LobbyManager() {
        this.nextLobbyId = 1;
        this.lobbies = new HashMap<>();
    }

    /**
     * Creates a new lobby and registers its host.
     *
     * @param host player creating the lobby
     * @return created lobby
     */
    public Lobby createLobby(Player host) {
        if (host == null) {
            throw new IllegalArgumentException("Host player cannot be null");
        }

        Lobby lobby = new Lobby(this.nextLobbyId++, host);
        this.lobbies.put(lobby.getId(), lobby);
        return lobby;
    }

    /**
     * Adds a player to an existing lobby if its status still allows joins.
     *
     * @param lobbyId target lobby identifier
     * @param player player joining the lobby
     */
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

    /**
     * Removes a player from a lobby.
     *
     * @param lobbyId target lobby identifier
     * @param player player leaving the lobby
     */
    public void exitLobby(int lobbyId, Player player) {
        this.removePlayer(lobbyId, player);
    }

    /**
     * Removes a player from a lobby and updates host or lobby state if needed.
     * If the last player leaves, the lobby is closed and removed.
     *
     * @param lobbyId target lobby identifier
     * @param player player to remove
     */
    public void removePlayer(int lobbyId, Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }

        Lobby lobby = this.resolveLobby(lobbyId);
        lobby.removePlayer(player);

        if (lobby.getPlayers().isEmpty()) {
            lobby.setStatus(LobbyStatus.CLOSED);
            this.lobbies.remove(lobbyId);
            return;
        }

        if (!lobby.canStart() && lobby.getStatus() == LobbyStatus.READY) {
            lobby.setStatus(LobbyStatus.WAITING);
        }
    }

    /**
     * Checks whether a lobby has enough players to start.
     *
     * @param lobbyId target lobby identifier
     * @return {@code true} if the lobby can start, {@code false} otherwise
     */
    public boolean canStart(int lobbyId) {
        Lobby lobby = this.resolveLobby(lobbyId);
        return lobby.canStart();
    }

    /**
     * Returns all lobbies that are still joinable or waiting to start.
     *
     * @return ordered list of open lobbies
     */
    public List<Lobby> listOpenLobbies() {
        return this.lobbies.values().stream()
                .filter(lobby -> lobby.getStatus() != LobbyStatus.CLOSED)
                .filter(lobby -> lobby.getStatus() != LobbyStatus.STARTED)
                .sorted(Comparator.comparingInt(Lobby::getId))
                .toList();
    }

    /**
     * Retrieves a lobby by id.
     *
     * @param lobbyId lobby identifier
     * @return optional containing the lobby if present
     */
    public Optional<Lobby> getLobby(int lobbyId) {
        return Optional.ofNullable(this.lobbies.get(lobbyId));
    }

    // TODO: usare il tipo di ritorno `Game` e rimuovere la reflection quando la classe Game sarà disponibile in questo branch.
    /**
     * Creates a game instance from a ready lobby and marks the lobby as started.
     * Reflection is used temporarily because the concrete {@code Game} type is not yet available in this branch.
     *
     * @param lobbyId lobby identifier
     * @return created game instance
     */
    public Object createGameFromLobby(int lobbyId) {
        Lobby lobby = this.resolveLobby(lobbyId);

        if (lobby.getStatus() != LobbyStatus.READY) {
            throw new IllegalStateException("Cannot create game from lobby with status: " + lobby.getStatus());
        }

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

    /**
     * Resolves a lobby by id or fails if it does not exist.
     *
     * @param lobbyId lobby identifier
     * @return resolved lobby
     */
    private Lobby resolveLobby(int lobbyId) {
        Lobby lobby = this.lobbies.get(lobbyId);

        if (lobby == null) {
            throw new IllegalArgumentException("Lobby not found: " + lobbyId);
        }

        return lobby;
    }

    /**
     * Verifies that a lobby still accepts new players.
     *
     * @param lobby lobby to validate
     */
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
