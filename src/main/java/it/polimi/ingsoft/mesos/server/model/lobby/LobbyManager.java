package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.controller.Factory.LobbyPlayerFactory;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.NoOpSavedMatchService;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchRestoreResult;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchService;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.server.model.user.User;

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
 * It is responsible for creating lobbies, adding and removing users,
 * exposing open lobbies and starting a game from a ready lobby.
 */
public class LobbyManager {
    private int nextLobbyId;
    private final Map<Integer, Lobby> lobbies;
    private final GameFactory gameFactory;
    private final LobbyPlayerFactory lobbyPlayerFactory;

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
        this.lobbyPlayerFactory = new LobbyPlayerFactory();
    }

    /**
     * Creates a new lobby and registers its host.
     *
     * @param host user creating the lobby
     * @param chosenCapacity the number of players chosen for this match
     * @return created lobby
     */
    public Lobby createLobby(User host, int chosenCapacity) {
        if (host == null) {
            throw new IllegalArgumentException("Host user cannot be null");
        }
        if(isUserInAnyLobby(host)) {
            throw new IllegalStateException("Host already in a lobby");
        }

        Lobby lobby = new Lobby(this.nextLobbyId++, host, chosenCapacity);
        this.lobbies.put(lobby.getId(), lobby);
        return lobby;
    }

    /**
     * Creates a resumable lobby for a suspended match waiting to be restored.
     *
     * @param resumableGameId persisted match identifier
     * @param reservedNicknames original user nicknames that are allowed to rejoin
     * @return created resumable lobby
     */
    public Lobby createResumeLobby(String resumableGameId, List<String> reservedNicknames) {
        Lobby lobby = Lobby.resumable(this.nextLobbyId++, resumableGameId, reservedNicknames);
        this.lobbies.put(lobby.getId(), lobby);
        return lobby;
    }

    /**
     * Adds a user to an existing lobby if its status still allows joins.
     *
     * @param lobbyId target lobby identifier
     * @param user user joining the lobby
     */
    public void joinLobby(int lobbyId, User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if(isUserInAnyLobby(user)) {
            throw new IllegalStateException("User already in a lobby");
        }

        Lobby lobby = this.resolveLobby(lobbyId);
        this.ensureLobbyAcceptsUsers(lobby);
        lobby.addUser(user);

        if (lobby.canStart()
                && (lobby.getStatus() == LobbyStatus.WAITING || lobby.getStatus() == LobbyStatus.RESUMABLE)) {
            lobby.setStatus(LobbyStatus.READY);
        }
    }

    /**
     * Removes a user from a lobby.
     *
     * @param lobbyId target lobby identifier
     * @param user user leaving the lobby
     */
    public void exitLobby(int lobbyId, User user) {
        this.removeUser(lobbyId, user);
    }

    /**
     * Removes a user from a lobby and updates host or lobby state if needed.
     * If the last user leaves, the lobby is closed and removed.
     *
     * @param lobbyId target lobby identifier
     * @param user user to remove
     */
    public void removeUser(int lobbyId, User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        Lobby lobby = this.resolveLobby(lobbyId);
        lobby.removeUser(user);
        boolean isLobbyEmpty = lobby.getUsers().isEmpty() && !lobby.isResumable();
        if (isLobbyEmpty) {
            lobby.setStatus(LobbyStatus.CLOSED);
            this.lobbies.remove(lobbyId);
            return;
        }

        if (!lobby.canStart() && lobby.getStatus() == LobbyStatus.READY) {
            lobby.setStatus(lobby.isResumable() ? LobbyStatus.RESUMABLE : LobbyStatus.WAITING);
        }
    }

    /**
     * Checks whether a lobby has reached its chosen capacity.
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
        return this.createGameFromLobby(lobbyId, savedMatchService, null);
    }

    /**
     * Creates a game orchestrator from a ready lobby and wires both save-match persistence and
     * final ranking registration for that match.
     *
     * @param lobbyId lobby identifier
     * @param savedMatchService persistence service selected for the current runtime mode
     * @param rankingService ranking service used at match completion; nullable when unavailable
     * @return created game orchestrator
     */
    public GameOrchestrator createGameFromLobby(int lobbyId,
                                                SavedMatchService savedMatchService,
                                                RankingService rankingService) {
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

        List<Player> gamePlayers = this.lobbyPlayerFactory.createPlayers(lobby.getUsers());

        GameOrchestrator gameOrchestrator = new GameOrchestrator(
                this.gameFactory,
                new ArrayList<>(gamePlayers),
                lobby.getHostNickname(),
                savedMatchService,
                rankingService
        );
        lobby.setStatus(LobbyStatus.STARTED);
        return gameOrchestrator;
    }

    /**
     * Restores a previously persisted match from a ready resumable lobby.
     *
     * @param lobbyId resumable lobby identifier
     * @param savedMatchService application service able to rebuild the match aggregate
     * @return created game orchestrator
     */
    public GameOrchestrator resumeGameFromLobby(int lobbyId, SavedMatchService savedMatchService) {
        return this.resumeGameFromLobby(lobbyId, savedMatchService, null);
    }

    /**
     * Restores a previously persisted match from a ready resumable lobby and wires both save-match
     * persistence and final ranking registration.
     *
     * @param lobbyId resumable lobby identifier
     * @param savedMatchService application service able to rebuild the match aggregate
     * @param rankingService ranking service used at match completion; nullable when unavailable
     * @return created game orchestrator
     */
    public GameOrchestrator resumeGameFromLobby(int lobbyId,
                                                SavedMatchService savedMatchService,
                                                RankingService rankingService) {
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

        SavedMatchRestoreResult restoredMatch = savedMatchService.restoreMatch(lobby.getResumableGameId());
        Game restoredGame = restoredMatch.game();
        this.ensureRestoredRosterMatchesLobby(lobby, restoredGame);

        GameOrchestrator gameOrchestrator = new GameOrchestrator(
                restoredGame,
                restoredMatch.hostNickname(),
                savedMatchService,
                rankingService
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
     * Verifies that a lobby still accepts new users.
     *
     * @param lobby lobby to validate
     */
    private void ensureLobbyAcceptsUsers(Lobby lobby) {
        if (lobby.getStatus() == LobbyStatus.CLOSED || lobby.getStatus() == LobbyStatus.STARTED) {
            throw new IllegalStateException("Cannot join lobby with status: " + lobby.getStatus());
        }
    }

    /**
     * Verifies that the restored match roster matches the reserved resumable lobby roster.
     */
    private void ensureRestoredRosterMatchesLobby(Lobby lobby, Game restoredGame) {
        Set<String> reservedNicknames = new HashSet<>(lobby.getReservedNicknames());
        Set<String> restoredNicknames = restoredGame.getPlayers().stream()
                .map(Player::getNickname)
                .collect(java.util.stream.Collectors.toSet());

        if (!reservedNicknames.equals(restoredNicknames)) {
            throw new IllegalStateException("Restored match roster does not match resumable lobby roster");
        }
    }

    /**
     * Checks whether the specified user is currently present in any active lobby.
     *
     * @param user the user to check for presence
     * @return {@code true} if the user is found in at least one lobby, {@code false} otherwise
     */
    private boolean isUserInAnyLobby(User user) {
        return this.lobbies.values().stream()
                .anyMatch(lobby -> lobby.getUsers().contains(user));
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
