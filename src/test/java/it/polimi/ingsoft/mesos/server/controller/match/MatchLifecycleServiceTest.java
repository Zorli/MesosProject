package it.polimi.ingsoft.mesos.server.controller.match;

import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.game.TurnState;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.lobby.LobbyManager;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.NoOpSavedMatchService;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchCheckpointType;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchRestoreResult;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchService;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.server.model.user.User;
import it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchLifecycleServiceTest {

    @Test
    void startMatchFromReadyLobbyCreatesRuntimeAndRegistersIt() {
        MatchRegistry registry = new MatchRegistry();
        LobbyManager lobbyManager = new LobbyManager();
        MatchLifecycleService lifecycle = new MatchLifecycleService(
                lobbyManager,
                registry,
                NoOpSavedMatchService.INSTANCE,
                null
        );
        Lobby lobby = readyLobby(lobbyManager);

        MatchRuntime runtime = lifecycle.startMatchFromLobby(lobby.getId());

        assertSame(runtime, registry.require(runtime.getGameId()));
        assertEquals(runtime.getGameId(), runtime.getOrchestrator().getGame().getId());

        lifecycle.closeMatch(runtime.getGameId());
    }

    @Test
    void startMatchFromNonReadyLobbyDoesNotRegisterRuntime() {
        MatchRegistry registry = new MatchRegistry();
        LobbyManager lobbyManager = new LobbyManager();
        MatchLifecycleService lifecycle = new MatchLifecycleService(
                lobbyManager,
                registry,
                NoOpSavedMatchService.INSTANCE,
                null
        );
        Lobby lobby = lobbyManager.createLobby(new User("Alice"), 2);

        assertThrows(IllegalStateException.class, () -> lifecycle.startMatchFromLobby(lobby.getId()));

        assertTrue(registry.find(1).isEmpty());
    }

    @Test
    void resumeMatchFromReadyResumableLobbyCreatesRuntimeAndRegistersIt() {
        MatchRegistry registry = new MatchRegistry();
        LobbyManager lobbyManager = new LobbyManager();
        RecordingRestoreService restoreService = new RecordingRestoreService(restoredGame("Alice", "Bob"), "Alice");
        MatchLifecycleService lifecycle = new MatchLifecycleService(lobbyManager, registry, restoreService, null);
        Lobby lobby = lobbyManager.createResumeLobby("game-77", List.of("Alice", "Bob"));
        lobbyManager.joinLobby(lobby.getId(), new User("Alice"));
        lobbyManager.joinLobby(lobby.getId(), new User("Bob"));

        MatchRuntime runtime = lifecycle.resumeMatchFromLobby(lobby.getId());

        assertEquals("game-77", restoreService.capturedGameId);
        assertSame(runtime, registry.require(runtime.getGameId()));

        lifecycle.closeMatch(runtime.getGameId());
    }

    @Test
    void resumeMatchWithRosterMismatchDoesNotRegisterRuntime() {
        MatchRegistry registry = new MatchRegistry();
        LobbyManager lobbyManager = new LobbyManager();
        RecordingRestoreService restoreService = new RecordingRestoreService(restoredGame("Alice", "Carol"), "Alice");
        MatchLifecycleService lifecycle = new MatchLifecycleService(lobbyManager, registry, restoreService, null);
        Lobby lobby = lobbyManager.createResumeLobby("game-77", List.of("Alice", "Bob"));
        lobbyManager.joinLobby(lobby.getId(), new User("Alice"));
        lobbyManager.joinLobby(lobby.getId(), new User("Bob"));

        assertThrows(IllegalStateException.class, () -> lifecycle.resumeMatchFromLobby(lobby.getId()));

        assertTrue(registry.find(77).isEmpty());
    }

    @Test
    void registrationFailureClosesNewlyCreatedRuntime() {
        MatchRegistry registry = new MatchRegistry();
        LobbyManager lobbyManager = new LobbyManager(new FixedIdGameFactory(501));
        List<MatchRuntime> createdRuntimes = new ArrayList<>();
        MatchLifecycleService lifecycle = new MatchLifecycleService(
                lobbyManager,
                registry,
                NoOpSavedMatchService.INSTANCE,
                null,
                orchestrator -> {
                    MatchRuntime runtime = new MatchRuntime(orchestrator);
                    createdRuntimes.add(runtime);
                    return runtime;
                }
        );
        Lobby firstLobby = readyLobby(lobbyManager, "Alice", "Bob");
        Lobby secondLobby = readyLobby(lobbyManager, "Carol", "Dave");
        MatchRuntime firstRuntime = lifecycle.startMatchFromLobby(firstLobby.getId());

        assertThrows(MatchAlreadyRegisteredException.class, () -> lifecycle.startMatchFromLobby(secondLobby.getId()));

        assertSame(firstRuntime, registry.require(501));
        assertEquals(2, createdRuntimes.size());
        assertThrows(IllegalStateException.class, () -> createdRuntimes.get(1).submit(() -> "closed"));
        lifecycle.closeMatch(501);
    }

    @Test
    void lifecycleDoesNotCallSetupMatchAutomatically() {
        MatchRegistry registry = new MatchRegistry();
        LobbyManager lobbyManager = new LobbyManager();
        RecordingRestoreService savedMatchService = new RecordingRestoreService(restoredGame("Alice", "Bob"), "Alice");
        MatchLifecycleService lifecycle = new MatchLifecycleService(lobbyManager, registry, savedMatchService, null);
        Lobby lobby = readyLobby(lobbyManager);

        MatchRuntime runtime = lifecycle.startMatchFromLobby(lobby.getId());

        assertEquals(0, savedMatchService.initializeCalls);
        assertFalse(runtime.getOrchestrator().isFrozen());

        lifecycle.closeMatch(runtime.getGameId());
    }

    @Test
    void closeMatchDelegatesToRegistryAndClosesRuntime() {
        MatchRegistry registry = new MatchRegistry();
        LobbyManager lobbyManager = new LobbyManager();
        MatchLifecycleService lifecycle = new MatchLifecycleService(
                lobbyManager,
                registry,
                NoOpSavedMatchService.INSTANCE,
                null
        );
        MatchRuntime runtime = lifecycle.startMatchFromLobby(readyLobby(lobbyManager).getId());

        lifecycle.closeMatch(runtime.getGameId());

        assertTrue(registry.find(runtime.getGameId()).isEmpty());
        assertThrows(IllegalStateException.class, () -> runtime.submit(() -> "closed"));
    }

    private static Lobby readyLobby(LobbyManager lobbyManager) {
        return readyLobby(lobbyManager, "Alice", "Bob");
    }

    private static Lobby readyLobby(LobbyManager lobbyManager, String host, String guest) {
        Lobby lobby = lobbyManager.createLobby(new User(host), 2);
        lobbyManager.joinLobby(lobby.getId(), new User(guest));
        return lobby;
    }

    private static Game restoredGame(String... nicknames) {
        ArrayList<Player> players = MesosTestFixtures.createPlayers(nicknames);
        return DefaultGameFactory.getInstance().createGame(players);
    }

    private static final class FixedIdGameFactory implements GameFactory {
        private final int gameId;

        private FixedIdGameFactory(int gameId) {
            this.gameId = gameId;
        }

        @Override
        public Game createGame(List<Player> players) {
            return new Game(
                    this.gameId,
                    new ArrayList<>(players),
                    new TurnState(),
                    new TurnOrderTrack(players, 0, 1, RoundPhase.PLACING_TOTEMS),
                    new Board(TribeDeck.fromOrderedCards(new ArrayList<>()), BuildingDeck.fromOrderedCards(new ArrayList<>()), List.of()),
                    Era.first,
                    GameStatus.IN_PROGRESS
            );
        }
    }

    private static final class RecordingRestoreService implements SavedMatchService {
        private final Game restoredGame;
        private final String hostNickname;
        private String capturedGameId;
        private int initializeCalls;

        private RecordingRestoreService(Game restoredGame, String hostNickname) {
            this.restoredGame = restoredGame;
            this.hostNickname = hostNickname;
        }

        @Override
        public boolean isPersistenceEnabled() {
            return true;
        }

        @Override
        public void initializeMatch(Game game, String hostNickname) {
            this.initializeCalls++;
        }

        @Override
        public void saveTurnStarted(Game game, String hostNickname) {
        }

        @Override
        public void saveEraChanged(Game game, String hostNickname) {
        }

        @Override
        public void saveTurnFinished(Game game, String hostNickname) {
        }

        @Override
        public void completeMatch(Game game, String hostNickname) {
        }

        @Override
        public void saveMatch(Game game, String hostNickname, SavedMatchCheckpointType checkpointType) {
        }

        @Override
        public SavedMatchRestoreResult restoreMatch(String gameId) {
            this.capturedGameId = gameId;
            return new SavedMatchRestoreResult(this.restoredGame, this.hostNickname);
        }

        @Override
        public void markPlayerOnline(String gameId, String nickname, String sessionToken) {
        }

        @Override
        public void markPlayerOffline(String gameId, String nickname) {
        }

        @Override
        public int deleteExpiredInProgressMatches() {
            return 0;
        }
    }
}
