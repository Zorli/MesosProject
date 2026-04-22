package it.polimi.ingsoft.mesos.server.controller.GameOrchestrator;

import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.game.TurnState;
import it.polimi.ingsoft.mesos.server.model.game.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;
import it.polimi.ingsoft.mesos.server.model.ranking.LeaderboardEntry;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchHistory;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchScore;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingResult;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.NoOpSavedMatchService;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchCheckpointType;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchRestoreResult;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchService;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.server.testing.AdvancedGameStateFixtures;
import it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    void gameBuildsCurrentPayloadAfterStateChanges() {
        ArrayList<Player> players = createPlayers(2);
        GameOrchestrator orchestrator = new GameOrchestrator(
                DefaultGameFactory.getInstance(),
                players,
                "Alice",
                NoOpSavedMatchService.INSTANCE
        );

        GamePayload initialPayload = orchestrator.getGame().buildPayload();
        assertEquals("SETUP", initialPayload.status);

        orchestrator.setupMatch();

        GamePayload updatedPayload = orchestrator.getGame().buildPayload();
        assertEquals("IN_PROGRESS", updatedPayload.status);
        assertEquals(RoundPhase.PLACING_TOTEMS.name(), updatedPayload.turnOrder.currentRoundPhase);
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

        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer().getNickname(), "B");
        assertEquals(0, savedMatchService.turnStartedCalls);

        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer().getNickname(), "C");
        assertEquals(1, savedMatchService.turnStartedCalls);
        assertEquals(RoundPhase.PLAYER_DRAWS, orchestrator.getGame().getTurnOrderTrack().getCurrentRoundPhase());
    }

    @Test
    void chooseCardPersistsTurnFinishedWhenDrawTurnEnds() {
        ArrayList<Player> players = createPlayers(2);
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();

        GameOrchestrator orchestrator = new GameOrchestrator(
                MesosTestFixtures.createTwoPlayerGameplayFactory(),
                players,
                "Alice",
                savedMatchService
        );
        moveGameToDrawPhase(orchestrator);

        Card chosenCard = MesosTestFixtures.findLowerRowCard(orchestrator.getGame(), 10);
        orchestrator.chooseCard(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer().getNickname(), chosenCard.getId());

        assertEquals(1, savedMatchService.turnFinishedCalls);
        assertEquals("Alice", savedMatchService.lastHostNickname);
        assertEquals("Bob", orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer().getNickname());
        assertEquals(RoundPhase.PLAYER_DRAWS, orchestrator.getGame().getTurnOrderTrack().getCurrentRoundPhase());
    }

    @Test
    void extraDrawnPersistsTurnFinishedWhenExtraDrawFlowAdvances() {
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        Game game = AdvancedGameStateFixtures.createExtraDrawScenario();
        GameOrchestrator orchestrator = new GameOrchestrator(game, "Alice", savedMatchService);

        orchestrator.extraDrawn(game.getTurnOrderTrack().getCurrentPlayer().getNickname(), MesosTestFixtures.findUpperRowCard(game, 66).getId());

        assertEquals(1, savedMatchService.turnFinishedCalls);
        assertEquals(RoundPhase.RESOLVING_EFFECTS, game.getTurnOrderTrack().getCurrentRoundPhase());
    }

    @Test
    void resolveEffectsPersistsTurnFinishedWhenRoundMovesToResettingBoard() {
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        Game game = createResolvingEffectsGame(4);
        GameOrchestrator orchestrator = new GameOrchestrator(game, "Alice", savedMatchService);

        orchestrator.resolveEffects();

        assertEquals(1, savedMatchService.turnFinishedCalls);
        assertEquals(0, savedMatchService.completedCalls);
        assertEquals(RoundPhase.RESETTING_BOARD, game.getTurnOrderTrack().getCurrentRoundPhase());
    }

    @Test
    void resolveEffectsPersistsMatchCompletedWhenEnteringFinalScoring() {
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        Game game = createResolvingEffectsGame(10);
        GameOrchestrator orchestrator = new GameOrchestrator(game, "Alice", savedMatchService);

        orchestrator.resolveEffects();

        assertEquals(1, savedMatchService.completedCalls);
        assertEquals(0, savedMatchService.turnFinishedCalls);
        assertEquals(RoundPhase.FINAL_SCORING, game.getTurnOrderTrack().getCurrentRoundPhase());
    }

    @Test
    void resetBoardPersistsTurnStartedWhenEraDoesNotChange() {
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        Game game = createResettingBoardGame(false);
        GameOrchestrator orchestrator = new GameOrchestrator(game, "Alice", savedMatchService);

        orchestrator.resetBoard();

        assertEquals(1, savedMatchService.turnStartedCalls);
        assertEquals(0, savedMatchService.eraChangedCalls);
        assertEquals(RoundPhase.PLACING_TOTEMS, game.getTurnOrderTrack().getCurrentRoundPhase());
        assertEquals(2, game.getTurnOrderTrack().getRoundNumber());
    }

    @Test
    void resetBoardPersistsEraChangedWhenBoardOpensNewEra() {
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        Game game = createResettingBoardGame(true);
        GameOrchestrator orchestrator = new GameOrchestrator(game, "Alice", savedMatchService);

        orchestrator.resetBoard();

        assertEquals(0, savedMatchService.turnStartedCalls);
        assertEquals(1, savedMatchService.eraChangedCalls);
        assertEquals(Era.second, game.getCurrentEra());
        assertEquals(RoundPhase.PLACING_TOTEMS, game.getTurnOrderTrack().getCurrentRoundPhase());
    }

    @Test
    void calculateFinalScorePersistsMatchCompleted() {
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        Game game = AdvancedGameStateFixtures.createFinalScoringScenario();
        GameOrchestrator orchestrator = new GameOrchestrator(game, "Alice", savedMatchService);

        orchestrator.calculateFinalScore();

        assertEquals(1, savedMatchService.completedCalls);
        assertEquals(GameStatus.FINISHED, game.getStatus());
    }

    @Test
    void calculateFinalScorePersistsFinalSnapshotBeforeRecordingRanking() {
        List<String> events = new ArrayList<>();
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService(events);
        RecordingRankingService rankingService = new RecordingRankingService(events);
        Game game = AdvancedGameStateFixtures.createFinalScoringScenario();
        GameOrchestrator orchestrator = new GameOrchestrator(game, "Alice", savedMatchService, rankingService);

        Optional<RankingResult> rankingResult = orchestrator.calculateFinalScore();

        assertEquals(List.of("save:completed", "ranking:recorded"), events);
        assertTrue(rankingResult.isPresent());
        assertEquals(1, savedMatchService.completedCalls);
        assertEquals(1, rankingService.recordCalls);
        assertEquals("901", rankingService.gameId);
        assertEquals(2, rankingService.playerCount);
        assertEquals("Alice", rankingService.requestingNickname);
        assertEquals(
                game.getPlayers().stream()
                        .map(player -> new MatchScore(player.getNickname(), player.getPrestigePoints()))
                        .toList(),
                rankingService.finalScores
        );
        assertEquals(1, rankingResult.orElseThrow().playerRank());
    }

    @Test
    void calculateFinalScoreReturnsEmptyRankingResultWhenRankingServiceIsNotConfigured() {
        List<String> events = new ArrayList<>();
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService(events);
        Game game = AdvancedGameStateFixtures.createFinalScoringScenario();
        GameOrchestrator orchestrator = new GameOrchestrator(game, "Alice", savedMatchService);

        Optional<RankingResult> rankingResult = orchestrator.calculateFinalScore();

        assertTrue(rankingResult.isEmpty());
        assertEquals(List.of("save:completed"), events);
        assertEquals(1, savedMatchService.completedCalls);
    }

    @Test
    void setupMatchFreezesMatchWhenInitialCheckpointSaveFails() {
        ArrayList<Player> players = createPlayers(2);
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        savedMatchService.initializeFailure = new IllegalStateException("db down");

        GameOrchestrator orchestrator = new GameOrchestrator(
                DefaultGameFactory.getInstance(),
                players,
                "Alice",
                savedMatchService
        );

        MatchPersistenceFailureException exception = assertThrows(
                MatchPersistenceFailureException.class,
                orchestrator::setupMatch
        );

        assertEquals("Failed to persist checkpoint GAME_INITIALIZED; match is now frozen", exception.getMessage());
        assertEquals("db down", exception.getCause().getMessage());
        assertTrue(orchestrator.isFrozen());
        assertEquals(1, savedMatchService.initializeCalls);
        assertThrows(MatchFrozenException.class, orchestrator::setupMatch);
        assertThrows(MatchFrozenException.class, () -> orchestrator.moveTotem(players.getFirst().getNickname(), "B"));
    }

    @Test
    void moveTotemFreezesMatchWhenTurnStartedSaveFails() {
        ArrayList<Player> players = createPlayers(2);
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        savedMatchService.turnStartedFailure = new IllegalStateException("postgres unavailable");

        GameOrchestrator orchestrator = new GameOrchestrator(
                DefaultGameFactory.getInstance(),
                players,
                "Alice",
                savedMatchService
        );
        orchestrator.setupMatch();

        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer().getNickname(), "B");

        MatchPersistenceFailureException exception = assertThrows(
                MatchPersistenceFailureException.class,
                () -> orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer().getNickname(), "C")
        );

        assertEquals("Failed to persist checkpoint TURN_STARTED; match is now frozen", exception.getMessage());
        assertEquals("postgres unavailable", exception.getCause().getMessage());
        assertTrue(orchestrator.isFrozen());
        assertEquals(1, savedMatchService.turnStartedCalls);
        assertEquals(RoundPhase.PLAYER_DRAWS, orchestrator.getGame().getTurnOrderTrack().getCurrentRoundPhase());
        assertThrows(MatchFrozenException.class, () -> orchestrator.moveTotem(players.getFirst().getNickname(), "D"));
    }

    @Test
    void chooseCardFreezesMatchWhenTurnFinishedSaveFails() {
        ArrayList<Player> players = createPlayers(2);
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        savedMatchService.turnFinishedFailure = new IllegalStateException("turn-finished save failed");

        GameOrchestrator orchestrator = new GameOrchestrator(
                MesosTestFixtures.createTwoPlayerGameplayFactory(),
                players,
                "Alice",
                savedMatchService
        );
        moveGameToDrawPhase(orchestrator);

        MatchPersistenceFailureException exception = assertThrows(
                MatchPersistenceFailureException.class,
                () -> orchestrator.chooseCard(
                        orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(),
                        MesosTestFixtures.findLowerRowCard(orchestrator.getGame().getId(), 10)
                )
        );

        assertEquals("Failed to persist checkpoint TURN_FINISHED; match is now frozen", exception.getMessage());
        assertEquals("turn-finished save failed", exception.getCause().getMessage());
        assertTrue(orchestrator.isFrozen());
        assertThrows(
                MatchFrozenException.class,
                () -> orchestrator.chooseCard(
                        orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(),
                        MesosTestFixtures.findUpperRowCard(orchestrator.getGame().getId(), 3)
                )
        );
    }

    @Test
    void resetBoardFreezesMatchWhenEraChangedSaveFails() {
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        savedMatchService.eraChangedFailure = new IllegalStateException("era save failed");
        Game game = createResettingBoardGame(true);
        GameOrchestrator orchestrator = new GameOrchestrator(game, "Alice", savedMatchService);

        MatchPersistenceFailureException exception = assertThrows(
                MatchPersistenceFailureException.class,
                orchestrator::resetBoard
        );

        assertEquals("Failed to persist checkpoint ERA_CHANGED; match is now frozen", exception.getMessage());
        assertEquals("era save failed", exception.getCause().getMessage());
        assertTrue(orchestrator.isFrozen());
        assertThrows(MatchFrozenException.class, orchestrator::resetBoard);
    }

    @Test
    void calculateFinalScoreFreezesMatchWhenCompletedCheckpointSaveFails() {
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        savedMatchService.completedFailure = new IllegalStateException("completion save failed");
        Game game = AdvancedGameStateFixtures.createFinalScoringScenario();
        GameOrchestrator orchestrator = new GameOrchestrator(game, "Alice", savedMatchService);

        MatchFinalizationFailureException exception = assertThrows(
                MatchFinalizationFailureException.class,
                orchestrator::calculateFinalScore
        );

        assertEquals("Failed to finalize completed match; match is now frozen", exception.getMessage());
        assertEquals("completion save failed", exception.getCause().getMessage());
        assertTrue(orchestrator.isFrozen());
        assertThrows(MatchFrozenException.class, orchestrator::calculateFinalScore);
    }

    @Test
    void calculateFinalScoreFreezesMatchWhenRankingRecordingFailsAfterSavingFinalSnapshot() {
        List<String> events = new ArrayList<>();
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService(events);
        RecordingRankingService rankingService = new RecordingRankingService(events);
        rankingService.failure = new IllegalStateException("ranking write failed");
        Game game = AdvancedGameStateFixtures.createFinalScoringScenario();
        GameOrchestrator orchestrator = new GameOrchestrator(game, "Alice", savedMatchService, rankingService);

        MatchFinalizationFailureException exception = assertThrows(
                MatchFinalizationFailureException.class,
                orchestrator::calculateFinalScore
        );

        assertEquals("Failed to finalize completed match; match is now frozen", exception.getMessage());
        assertEquals("ranking write failed", exception.getCause().getMessage());
        assertEquals(List.of("save:completed", "ranking:recorded"), events);
        assertEquals(1, savedMatchService.completedCalls);
        assertEquals(1, rankingService.recordCalls);
        assertTrue(orchestrator.isFrozen());
        assertThrows(MatchFrozenException.class, orchestrator::calculateFinalScore);
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

        orchestrator.moveTotem(players.getFirst().getNickname(), "B");
        assertEquals(0, savedMatchService.turnStartedCalls);
    }

    private static ArrayList<Player> createPlayers(int playerCount) {
        return MesosTestFixtures.createPlayers(playerCount);
    }

    private static void moveGameToDrawPhase(GameOrchestrator orchestrator) {
        orchestrator.setupMatch();
        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer().getNickname(), "B");
        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer().getNickname(), "C");
    }

    private static Player player(String nickname, TotemColor color) {
        return new Player(nickname, 0, 0, color, new Tribe());
    }

    private static Game createResolvingEffectsGame(int roundNumber) {
        Player alice = player("Alice", TotemColor.RED);
        return new Game(
                roundNumber,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, roundNumber, RoundPhase.RESOLVING_EFFECTS),
                new ResolveEffectsBoard(),
                Era.first,
                GameStatus.IN_PROGRESS
        );
    }

    private static Game createResettingBoardGame(boolean eraChanged) {
        Player alice = player("Alice", TotemColor.RED);
        return new Game(
                eraChanged ? 20 : 21,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.RESETTING_BOARD),
                new ResetBoardRecordingBoard(eraChanged),
                Era.first,
                GameStatus.IN_PROGRESS
        );
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
        private final List<String> events;
        private int initializeCalls;
        private int turnStartedCalls;
        private int turnFinishedCalls;
        private int eraChangedCalls;
        private int completedCalls;
        private Game lastGame;
        private String lastHostNickname;
        private RuntimeException initializeFailure;
        private RuntimeException turnStartedFailure;
        private RuntimeException turnFinishedFailure;
        private RuntimeException eraChangedFailure;
        private RuntimeException completedFailure;

        private RecordingSavedMatchService() {
            this(new ArrayList<>());
        }

        private RecordingSavedMatchService(List<String> events) {
            this.events = events;
        }

        @Override
        public boolean isPersistenceEnabled() {
            return true;
        }

        @Override
        public void initializeMatch(Game game, String hostNickname) {
            this.initializeCalls++;
            this.events.add("save:initialized");
            this.lastGame = game;
            this.lastHostNickname = hostNickname;
            if (this.initializeFailure != null) {
                throw this.initializeFailure;
            }
        }

        @Override
        public void saveTurnStarted(Game game, String hostNickname) {
            this.turnStartedCalls++;
            this.events.add("save:turn-started");
            this.lastGame = game;
            this.lastHostNickname = hostNickname;
            if (this.turnStartedFailure != null) {
                throw this.turnStartedFailure;
            }
        }

        @Override
        public void saveEraChanged(Game game, String hostNickname) {
            this.eraChangedCalls++;
            this.events.add("save:era-changed");
            this.lastGame = game;
            this.lastHostNickname = hostNickname;
            if (this.eraChangedFailure != null) {
                throw this.eraChangedFailure;
            }
        }

        @Override
        public void saveTurnFinished(Game game, String hostNickname) {
            this.turnFinishedCalls++;
            this.events.add("save:turn-finished");
            this.lastGame = game;
            this.lastHostNickname = hostNickname;
            if (this.turnFinishedFailure != null) {
                throw this.turnFinishedFailure;
            }
        }

        @Override
        public void completeMatch(Game game, String hostNickname) {
            this.completedCalls++;
            this.events.add("save:completed");
            this.lastGame = game;
            this.lastHostNickname = hostNickname;
            if (this.completedFailure != null) {
                throw this.completedFailure;
            }
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

    private static final class RecordingRankingService implements RankingService {
        private final List<String> events;
        private int recordCalls;
        private String gameId;
        private int playerCount;
        private Instant playedAtUtc;
        private String requestingNickname;
        private List<MatchScore> finalScores = List.of();
        private RuntimeException failure;

        private RecordingRankingService() {
            this(new ArrayList<>());
        }

        private RecordingRankingService(List<String> events) {
            this.events = events;
        }

        @Override
        public boolean registerUser(String nickname) {
            return true;
        }

        @Override
        public boolean isNicknameAvailable(String nickname) {
            return true;
        }

        @Override
        public RankingResult recordMatchAndGetRanking(String gameId,
                                                      int playerCount,
                                                      Instant playedAtUtc,
                                                      String requestingNickname,
                                                      List<MatchScore> finalScores) {
            this.recordCalls++;
            this.gameId = gameId;
            this.playerCount = playerCount;
            this.playedAtUtc = playedAtUtc;
            this.requestingNickname = requestingNickname;
            this.finalScores = List.copyOf(finalScores);
            this.events.add("ranking:recorded");

            if (this.failure != null) {
                throw this.failure;
            }

            return new RankingResult(
                    1,
                    List.of(new LeaderboardEntry(requestingNickname, 1500, 1, 1, 1))
            );
        }

        @Override
        public List<LeaderboardEntry> getLeaderboard() {
            return List.of();
        }

        @Override
        public List<MatchHistory> getMatchHistory() {
            return List.of();
        }

        @Override
        public List<MatchHistory> getMatchHistory(int playerCount) {
            return List.of();
        }
    }

    private static final class ResolveEffectsBoard extends Board {
        private ResolveEffectsBoard() {
            super(null, null, new ArrayList<>());
        }

        @Override
        public void resolveLowerEvents() {
        }
    }

    private static final class ResetBoardRecordingBoard extends Board {
        private final boolean eraChanged;

        private ResetBoardRecordingBoard(boolean eraChanged) {
            super(null, null, new ArrayList<>());
            this.eraChanged = eraChanged;
        }

        @Override
        public boolean resetBoardAfterRound(Era currentEra) {
            return this.eraChanged;
        }
    }
}
