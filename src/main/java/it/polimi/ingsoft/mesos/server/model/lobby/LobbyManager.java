package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.model.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.Orchestration.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.persistence.savedmatch.NoOpSavedMatchService;
import it.polimi.ingsoft.mesos.server.persistence.savedmatch.SavedMatchService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Coordinates the lifecycle of all active lobbies.
 * It is responsible for creating lobbies, adding and removing players,
 * exposing open lobbies and starting a game from a ready lobby.
 */
public class LobbyManager {
    private int nextLobbyId;
    private final Map<Integer, Lobby> lobbies;
    private final GameFactory gameFactory;

    /**
     * Creates an empty lobby manager with progressive lobby identifiers.
     */
    public LobbyManager() {
        this(DefaultGameFactory.getInstance());
    }

    /**
     * Creates an empty lobby manager with the provided game factory.
     *
     * @param gameFactory factory used to bootstrap new games
     */
    public LobbyManager(GameFactory gameFactory) {
        if (gameFactory == null) {
            throw new IllegalArgumentException("Game factory cannot be null");
        }

        this.nextLobbyId = 1;
        this.lobbies = new HashMap<>();
        this.gameFactory = gameFactory;
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
     * Creates a resumable lobby for a suspended match waiting to be restored.
     *
     * @param resumableGameId persisted match identifier
     * @param reservedPlayerNicknames original player nicknames that are allowed to rejoin
     * @return created resumable lobby
     */
    public Lobby createResumeLobby(String resumableGameId, List<String> reservedPlayerNicknames) {
        Lobby lobby = Lobby.resumable(this.nextLobbyId++, resumableGameId, reservedPlayerNicknames);
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

        if (lobby.canStart()
                && (lobby.getStatus() == LobbyStatus.WAITING || lobby.getStatus() == LobbyStatus.RESUMABLE)) {
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

        if (lobby.getPlayers().isEmpty() && !lobby.isResumable()) {
            lobby.setStatus(LobbyStatus.CLOSED);
            this.lobbies.remove(lobbyId);
            return;
        }

        if (!lobby.canStart() && lobby.getStatus() == LobbyStatus.READY) {
            lobby.setStatus(lobby.isResumable() ? LobbyStatus.RESUMABLE : LobbyStatus.WAITING);
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

    /**
     * Creates a game orchestrator from a ready lobby and marks the lobby as started.
     *
     * @param lobbyId lobby identifier
     * @return created game orchestrator
     */
    public GameOrchestrator createGameFromLobby(int lobbyId) {
        return this.createGameFromLobby(lobbyId, NoOpSavedMatchService.INSTANCE);
    }

    /**
     * Creates a game orchestrator from a ready lobby and wires save-match persistence for that match.
     *
     * @param lobbyId lobby identifier
     * @param savedMatchService persistence service selected for the current runtime mode
     * @return created game orchestrator
     */
    public GameOrchestrator createGameFromLobby(int lobbyId, SavedMatchService savedMatchService) {
        if (savedMatchService == null) {
            throw new IllegalArgumentException("Saved-match service cannot be null");
        }

        Lobby lobby = this.resolveLobby(lobbyId);

        if (lobby.getStatus() != LobbyStatus.READY) {
            throw new IllegalStateException("Cannot create game from lobby with status: " + lobby.getStatus());
        }

        if (!lobby.canStart()) {
            throw new IllegalStateException("Cannot create game from lobby that cannot start");
        }

        if (lobby.isResumable()) {
            throw new IllegalStateException("Resumable lobbies must restore a saved match instead of creating a new one");
        }

        GameOrchestrator gameOrchestrator = new GameOrchestrator(
                this.gameFactory,
                new ArrayList<>(lobby.getPlayers()),
                lobby.getHostNickname(),
                savedMatchService
        );
        lobby.setStatus(LobbyStatus.STARTED);
        return gameOrchestrator;
    }

    /**
     * Restores a previously persisted match from a ready resumable lobby.
     *
     * @param lobbyId resumable lobby identifier
     * @param restoreService application service able to rebuild the match aggregate
     * @return created game orchestrator
     */
    public GameOrchestrator resumeGameFromLobby(int lobbyId, SavedMatchService savedMatchService) {
        if (savedMatchService == null) {
            throw new IllegalArgumentException("Saved-match service cannot be null");
        }

        Lobby lobby = this.resolveLobby(lobbyId);

        if (!lobby.isResumable()) {
            throw new IllegalStateException("Cannot resume game from a non-resumable lobby");
        }

        if (lobby.getStatus() != LobbyStatus.READY) {
            throw new IllegalStateException("Cannot resume game from lobby with status: " + lobby.getStatus());
        }

        if (!lobby.canStart()) {
            throw new IllegalStateException("Cannot resume game from lobby that cannot start");
        }

        Game restoredGame = savedMatchService.restoreGame(lobby.getResumableGameId());
        this.ensureRestoredRosterMatchesLobby(lobby, restoredGame);

        GameOrchestrator gameOrchestrator = new GameOrchestrator(
                restoredGame,
                lobby.getHostNickname(),
                savedMatchService
        );
        lobby.setStatus(LobbyStatus.STARTED);
        return gameOrchestrator;
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

    /**
     * Verifies that the restored match roster matches the reserved resumable lobby roster.
     */
    private void ensureRestoredRosterMatchesLobby(Lobby lobby, Game restoredGame) {
        Set<String> reservedNicknames = new HashSet<>(lobby.getReservedPlayerNicknames());
        Set<String> restoredNicknames = restoredGame.getPlayers().stream()
                .map(Player::getNickname)
                .collect(java.util.stream.Collectors.toSet());

        if (!reservedNicknames.equals(restoredNicknames)) {
            throw new IllegalStateException("Restored match roster does not match resumable lobby roster");
        }
    }

    /**
     * Returns a debug-friendly textual representation of the manager and its tracked lobbies.
     *
     * @return string containing the main manager fields and ordered lobby list
     */
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
