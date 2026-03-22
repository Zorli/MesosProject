package it.polimi.ingsoft.mesos.server.model.Orchestration;

import it.polimi.ingsoft.mesos.server.model.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.GameState.InvalidGameStatusException;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnState;
import it.polimi.ingsoft.mesos.server.model.GameState.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;
import it.polimi.ingsoft.mesos.server.persistence.savedmatch.NoOpSavedMatchService;
import it.polimi.ingsoft.mesos.server.persistence.savedmatch.SavedMatchCheckpointType;
import it.polimi.ingsoft.mesos.server.persistence.savedmatch.SavedMatchRestoreResult;
import it.polimi.ingsoft.mesos.server.persistence.savedmatch.SavedMatchService;
import it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests orchestration behavior around setup and persistence checkpoints.
 */
class GameOrchestratorTest {

    @Test
    void constructorWithFactoryAndPlayersUsesDefaultHostAndNoOpPersistence() {
        ArrayList<Player> players = createPlayers(2);
        RecordingGameFactory gameFactory = new RecordingGameFactory(DefaultGameFactory.getInstance().createGame(players));

        GameOrchestrator orchestrator = new GameOrchestrator(gameFactory, players);

        assertSame(gameFactory.gameToReturn, orchestrator.getGame());
        assertEquals(1, gameFactory.calls);
        assertIterableEquals(players, gameFactory.capturedPlayers);
        assertNotSame(players, gameFactory.capturedPlayers);
        assertFalse(orchestrator.isPersistenceEnabled());
    }

    @Test
    void restoredGameConstructorReusesExistingAggregate() {
        ArrayList<Player> players = new ArrayList<>();
        players.add(new Player("Alice", 0, 0, TotemColor.RED, new Tribe()));
        players.add(new Player("Bob", 0, 0, TotemColor.BLUE, new Tribe()));

        Game restoredGame = DefaultGameFactory.getInstance().createGame(players);

        GameOrchestrator orchestrator = new GameOrchestrator(restoredGame);

        assertSame(restoredGame, orchestrator.getGame());
        assertIterableEquals(restoredGame.getPlayers(), orchestrator.getPlayers());
    }

    @Test
    void setupMatchPersistsInitialCheckpoint() {
        ArrayList<Player> players = createPlayers(2);
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();

        GameOrchestrator orchestrator = new GameOrchestrator(
                DefaultGameFactory.getInstance(),
                players,
                "Alice",
                savedMatchService
        );

        orchestrator.setupMatch();

        assertEquals(1, savedMatchService.initializeCalls);
        assertSame(orchestrator.getGame(), savedMatchService.lastGame);
        assertEquals("Alice", savedMatchService.lastHostNickname);
        assertEquals(RoundPhase.PLACING_TOTEMS, orchestrator.getGame().getTurnOrderTrack().getCurrentRoundPhase());
        assertTrue(orchestrator.isPersistenceEnabled());
    }

    @Test
    void restoredGameConstructorWithPersistenceUsesProvidedCoordinator() {
        ArrayList<Player> players = createPlayers(2);
        Game restoredGame = DefaultGameFactory.getInstance().createGame(players);
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();

        GameOrchestrator orchestrator = new GameOrchestrator(restoredGame, "Alice", savedMatchService);

        assertSame(restoredGame, orchestrator.getGame());
        assertIterableEquals(restoredGame.getPlayers(), orchestrator.getPlayers());
        assertTrue(orchestrator.isPersistenceEnabled());
    }

    @Test
    void moveTotemPersistsTurnStartedWhenPlacementPhaseCompletes() {
        ArrayList<Player> players = createPlayers(2);
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();

        GameOrchestrator orchestrator = new GameOrchestrator(
                DefaultGameFactory.getInstance(),
                players,
                "Alice",
                savedMatchService
        );
        orchestrator.setupMatch();

        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "B");
        assertEquals(0, savedMatchService.turnStartedCalls);

        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "C");
        assertEquals(1, savedMatchService.turnStartedCalls);
        assertEquals(RoundPhase.PLAYER_DRAWS, orchestrator.getGame().getTurnOrderTrack().getCurrentRoundPhase());
    }

    @Test
    void constructorsRejectInvalidFactoryPlayersAndRestoredRoster() {
        ArrayList<Player> validPlayers = createPlayers(2);
        assertThrows(NullPointerException.class, () -> new GameOrchestrator(null, validPlayers, "Alice", NoOpSavedMatchService.INSTANCE));
        assertThrows(IllegalArgumentException.class, () -> new GameOrchestrator(DefaultGameFactory.getInstance(), null, "Alice", NoOpSavedMatchService.INSTANCE));
        assertThrows(IllegalArgumentException.class, () -> new GameOrchestrator(DefaultGameFactory.getInstance(), new ArrayList<>(), "Alice", NoOpSavedMatchService.INSTANCE));
        assertThrows(NullPointerException.class, () -> new GameOrchestrator((Game) null));
        assertThrows(NullPointerException.class, () -> new GameOrchestrator(null, "Alice", NoOpSavedMatchService.INSTANCE));

        Game emptyRosterGame = new Game(
                5,
                new ArrayList<>(),
                new TurnState(),
                new TurnOrderTrack(List.of(player("Ghost", TotemColor.RED)), 0, 1, RoundPhase.PLACING_TOTEMS),
                new Board(TribeDeck.fromOrderedCards(new ArrayList<>()), BuildingDeck.fromOrderedCards(new ArrayList<>()), List.of()),
                Era.first,
                GameStatus.IN_PROGRESS
        );
        assertThrows(IllegalArgumentException.class, () -> new GameOrchestrator(emptyRosterGame, "Alice", NoOpSavedMatchService.INSTANCE));
    }

    @Test
    void constructorsRejectBlankDerivedOrExplicitHostNickname() {
        ArrayList<Player> blankHostPlayers = new ArrayList<>();
        blankHostPlayers.add(new Player("   ", 0, 0, TotemColor.RED, new Tribe()));
        blankHostPlayers.add(new Player("Bob", 0, 0, TotemColor.BLUE, new Tribe()));

        assertThrows(IllegalArgumentException.class, () -> new GameOrchestrator(new RecordingGameFactory(DefaultGameFactory.getInstance().createGame(createPlayers(2))), blankHostPlayers));

        ArrayList<Player> validPlayers = createPlayers(2);
        assertThrows(IllegalArgumentException.class, () -> new GameOrchestrator(DefaultGameFactory.getInstance(), validPlayers, " ", NoOpSavedMatchService.INSTANCE));
    }

    @Test
    void setupAndMoveTotemPropagateFailuresWithoutPersistingCheckpoint() {
        ArrayList<Player> players = createPlayers(2);
        RecordingGameFactory failingFactory = new RecordingGameFactory(null);
        failingFactory.failure = new IllegalStateException("factory boom");
        assertThrows(IllegalStateException.class, () -> new GameOrchestrator(failingFactory, players));

        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        Game restoredGame = DefaultGameFactory.getInstance().createGame(players);
        GameOrchestrator orchestrator = new GameOrchestrator(restoredGame, "Alice", savedMatchService);

        assertThrows(InvalidGameStatusException.class, () -> orchestrator.moveTotem(players.getFirst(), "B"));
        assertEquals(0, savedMatchService.turnStartedCalls);
    }

    private static ArrayList<Player> createPlayers(int playerCount) {
        return MesosTestFixtures.createPlayers(playerCount);
    }

    private static Player player(String nickname, TotemColor color) {
        return new Player(nickname, 0, 0, color, new Tribe());
    }

    private static final class RecordingGameFactory implements GameFactory {
        private final Game gameToReturn;
        private int calls;
        private ArrayList<Player> capturedPlayers;
        private RuntimeException failure;

        private RecordingGameFactory(Game gameToReturn) {
            this.gameToReturn = gameToReturn;
        }

        @Override
        public Game createGame(java.util.List<Player> players) {
            this.calls++;
            this.capturedPlayers = new ArrayList<>(players);
            if (this.failure != null) {
                throw this.failure;
            }
            return this.gameToReturn;
        }
    }

    /**
     * Saved-match service spy capturing the checkpoints emitted by the orchestrator.
     */
    private static final class RecordingSavedMatchService implements SavedMatchService {
        private int initializeCalls;
        private int turnStartedCalls;
        private Game lastGame;
        private String lastHostNickname;

        @Override
        public boolean isPersistenceEnabled() {
            return true;
        }

        @Override
        public void initializeMatch(Game game, String hostNickname) {
            this.initializeCalls++;
            this.lastGame = game;
            this.lastHostNickname = hostNickname;
        }

        @Override
        public void saveTurnStarted(Game game, String hostNickname) {
            this.turnStartedCalls++;
            this.lastGame = game;
            this.lastHostNickname = hostNickname;
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
            throw new UnsupportedOperationException();
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
