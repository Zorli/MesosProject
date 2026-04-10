package it.polimi.ingsoft.mesos.server.controller.match;

import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.MatchFrozenException;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.MatchPersistenceFailureException;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.game.TurnState;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchCheckpointType;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchRestoreResult;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchService;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;
import it.polimi.ingsoft.mesos.server.model.ranking.LeaderboardEntry;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchHistory;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchScore;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingResult;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.server.testing.AdvancedGameStateFixtures;
import it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchRuntimeGameOrchestratorIntegrationTest {
    private static final long TIMEOUT_SECONDS = 2L;

    @Test
    void setupMatchThroughRuntimeReturnsUpdatedPayloadAfterCheckpoint() throws Exception {
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        GameOrchestrator orchestrator = new GameOrchestrator(
                DefaultGameFactory.getInstance(),
                MesosTestFixtures.createPlayers(2),
                "Alice",
                savedMatchService
        );

        try (MatchRuntime runtime = new MatchRuntime(orchestrator)) {
            GamePayload payload = await(runtime.setupMatch());

            assertEquals(1, savedMatchService.initializeCalls);
            assertEquals(orchestrator.getGame().getId(), payload.id);
            assertEquals(RoundPhase.PLACING_TOTEMS.name(), payload.turnOrder.currentRoundPhase);
        }
    }

    @Test
    void moveTotemThroughRuntimeReturnsPayloadAfterSuccessfulMutation() throws Exception {
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        GameOrchestrator orchestrator = new GameOrchestrator(
                DefaultGameFactory.getInstance(),
                MesosTestFixtures.createPlayers(2),
                "Alice",
                savedMatchService
        );

        try (MatchRuntime runtime = new MatchRuntime(orchestrator)) {
            await(runtime.setupMatch());
            await(runtime.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "B"));
            GamePayload payload = await(runtime.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "C"));

            assertEquals(1, savedMatchService.turnStartedCalls);
            assertEquals(RoundPhase.PLAYER_DRAWS.name(), payload.turnOrder.currentRoundPhase);
        }
    }

    @Test
    void chooseCardThroughRuntimePersistsBeforeFutureCompletes() throws Exception {
        List<String> events = new ArrayList<>();
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService(events);
        GameOrchestrator orchestrator = new GameOrchestrator(
                MesosTestFixtures.createTwoPlayerGameplayFactory(),
                MesosTestFixtures.createPlayers(2),
                "Alice",
                savedMatchService
        );

        try (MatchRuntime runtime = new MatchRuntime(orchestrator)) {
            moveGameToDrawPhase(runtime, orchestrator);

            GamePayload payload = await(runtime.chooseCard(
                    orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(),
                    MesosTestFixtures.findLowerRowCard(orchestrator.getGame(), 10)
            ));

            assertEquals(List.of("save:initialized", "save:turnStarted", "save:turnFinished"), events);
            assertEquals("Bob", payload.turnOrder.currentPlayerNickname);
        }
    }

    @Test
    void extraDrawnThroughRuntimeReturnsPayloadAfterSuccessfulMutation() throws Exception {
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        Game game = AdvancedGameStateFixtures.createExtraDrawScenario();
        GameOrchestrator orchestrator = new GameOrchestrator(game, "Alice", savedMatchService);

        try (MatchRuntime runtime = new MatchRuntime(orchestrator)) {
            GamePayload payload = await(runtime.extraDrawn(
                    game.getTurnOrderTrack().getCurrentPlayer(),
                    MesosTestFixtures.findUpperRowCard(game, 66)
            ));

            assertEquals(1, savedMatchService.turnFinishedCalls);
            assertEquals(RoundPhase.RESOLVING_EFFECTS.name(), payload.turnOrder.currentRoundPhase);
        }
    }

    @Test
    void resolveEffectsThroughRuntimeReturnsPayloadAfterTurnFinishedCheckpoint() throws Exception {
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        Game game = createResolvingEffectsGame(4);
        GameOrchestrator orchestrator = new GameOrchestrator(game, "Alice", savedMatchService);

        try (MatchRuntime runtime = new MatchRuntime(orchestrator)) {
            GamePayload payload = await(runtime.resolveEffects());

            assertEquals(1, savedMatchService.turnFinishedCalls);
            assertEquals(RoundPhase.RESETTING_BOARD.name(), payload.turnOrder.currentRoundPhase);
        }
    }

    @Test
    void resetBoardThroughRuntimeReturnsPayloadAfterTurnStartedCheckpoint() throws Exception {
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        Game game = createResettingBoardGame();
        GameOrchestrator orchestrator = new GameOrchestrator(game, "Alice", savedMatchService);

        try (MatchRuntime runtime = new MatchRuntime(orchestrator)) {
            GamePayload payload = await(runtime.resetBoard());

            assertEquals(1, savedMatchService.turnStartedCalls);
            assertEquals(RoundPhase.PLACING_TOTEMS.name(), payload.turnOrder.currentRoundPhase);
        }
    }

    @Test
    void persistenceFailureCompletesFutureExceptionallyAndFreezesMatch() throws Exception {
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        savedMatchService.turnStartedFailure = new IllegalStateException("db down");
        GameOrchestrator orchestrator = new GameOrchestrator(
                DefaultGameFactory.getInstance(),
                MesosTestFixtures.createPlayers(2),
                "Alice",
                savedMatchService
        );

        try (MatchRuntime runtime = new MatchRuntime(orchestrator)) {
            await(runtime.setupMatch());
            await(runtime.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "B"));

            ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> runtime.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "C")
                            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            );

            assertTrue(exception.getCause() instanceof MatchPersistenceFailureException);
            assertTrue(orchestrator.isFrozen());
        }
    }

    @Test
    void commandQueuedAfterFreezeFailsWithMatchFrozenException() throws Exception {
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        savedMatchService.initializeFailure = new IllegalStateException("db down");
        GameOrchestrator orchestrator = new GameOrchestrator(
                DefaultGameFactory.getInstance(),
                MesosTestFixtures.createPlayers(2),
                "Alice",
                savedMatchService
        );

        try (MatchRuntime runtime = new MatchRuntime(orchestrator)) {
            assertThrows(ExecutionException.class, () -> runtime.setupMatch().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> runtime.setupMatch().get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            );

            assertTrue(exception.getCause() instanceof MatchFrozenException);
        }
    }

    @Test
    void calculateFinalScoreReturnsPayloadAndRankingResult() throws Exception {
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService();
        RecordingRankingService rankingService = new RecordingRankingService();
        Game game = AdvancedGameStateFixtures.createFinalScoringScenario();
        GameOrchestrator orchestrator = new GameOrchestrator(game, "Alice", savedMatchService, rankingService);

        try (MatchRuntime runtime = new MatchRuntime(orchestrator)) {
            MatchCompletionResult result = await(runtime.calculateFinalScore());

            assertEquals(GameStatus.FINISHED.name(), result.gamePayload().status);
            assertTrue(result.rankingResult().isPresent());
            assertEquals(1, savedMatchService.completedCalls);
            assertEquals(1, rankingService.recordCalls);
        }
    }

    @Test
    void calculateFinalScorePersistsCompletedMatchBeforeRanking() throws Exception {
        List<String> events = new ArrayList<>();
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService(events);
        RecordingRankingService rankingService = new RecordingRankingService(events);
        GameOrchestrator orchestrator = new GameOrchestrator(
                AdvancedGameStateFixtures.createFinalScoringScenario(),
                "Alice",
                savedMatchService,
                rankingService
        );

        try (MatchRuntime runtime = new MatchRuntime(orchestrator)) {
            await(runtime.calculateFinalScore());

            assertEquals(List.of("save:completed", "ranking:recorded"), events);
        }
    }

    @Test
    void rankingFailureCompletesFutureExceptionallyAfterCompletedSave() {
        List<String> events = new ArrayList<>();
        RecordingSavedMatchService savedMatchService = new RecordingSavedMatchService(events);
        RecordingRankingService rankingService = new RecordingRankingService(events);
        rankingService.failure = new IllegalStateException("ranking down");
        GameOrchestrator orchestrator = new GameOrchestrator(
                AdvancedGameStateFixtures.createFinalScoringScenario(),
                "Alice",
                savedMatchService,
                rankingService
        );

        try (MatchRuntime runtime = new MatchRuntime(orchestrator)) {
            ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> runtime.calculateFinalScore().get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            );

            assertEquals("ranking down", exception.getCause().getCause().getMessage());
            assertEquals(List.of("save:completed", "ranking:recorded"), events);
            assertTrue(orchestrator.isFrozen());
        }
    }

    @Test
    void twoCommandsForSameMatchRunSequentially() throws Exception {
        GameOrchestrator orchestrator = new GameOrchestrator(
                DefaultGameFactory.getInstance(),
                MesosTestFixtures.createPlayers(2),
                "Alice",
                new RecordingSavedMatchService()
        );

        try (MatchRuntime runtime = new MatchRuntime(orchestrator)) {
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            AtomicBoolean secondRan = new AtomicBoolean(false);

            CompletableFuture<Void> first = runtime.submit(() -> {
                firstStarted.countDown();
                releaseFirst.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return null;
            });
            assertTrue(firstStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            CompletableFuture<Void> second = runtime.submit(() -> {
                secondRan.set(true);
                return null;
            });

            assertFalse(secondRan.get());
            releaseFirst.countDown();
            await(first);
            await(second);
            assertTrue(secondRan.get());
        }
    }

    @Test
    void commandsForDifferentMatchesCanRunConcurrently() throws Exception {
        MatchRuntime first = new MatchRuntime(new GameOrchestrator(
                DefaultGameFactory.getInstance(),
                MesosTestFixtures.createPlayers("Alice", "Bob"),
                "Alice",
                new RecordingSavedMatchService()
        ));
        MatchRuntime second = new MatchRuntime(new GameOrchestrator(
                DefaultGameFactory.getInstance(),
                MesosTestFixtures.createPlayers("Carol", "Dave"),
                "Carol",
                new RecordingSavedMatchService()
        ));
        try (first; second) {
            CountDownLatch bothStarted = new CountDownLatch(2);
            CountDownLatch releaseBoth = new CountDownLatch(1);

            CompletableFuture<Void> firstCommand = first.submit(() -> {
                bothStarted.countDown();
                releaseBoth.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return null;
            });
            CompletableFuture<Void> secondCommand = second.submit(() -> {
                bothStarted.countDown();
                releaseBoth.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return null;
            });

            assertTrue(bothStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            releaseBoth.countDown();
            await(firstCommand);
            await(secondCommand);
        }
    }

    @Test
    void currentPayloadThroughRuntimeUsesOrchestratorPayloadCache() throws Exception {
        GameOrchestrator orchestrator = new GameOrchestrator(
                DefaultGameFactory.getInstance(),
                MesosTestFixtures.createPlayers(2),
                "Alice",
                new RecordingSavedMatchService()
        );

        try (MatchRuntime runtime = new MatchRuntime(orchestrator)) {
            GamePayload first = await(runtime.getCurrentPayload());
            GamePayload second = await(runtime.getCurrentPayload());
            await(runtime.setupMatch());
            GamePayload third = await(runtime.getCurrentPayload());

            assertSame(first, second);
            assertNotSame(first, third);
        }
    }

    private static void moveGameToDrawPhase(MatchRuntime runtime, GameOrchestrator orchestrator) throws Exception {
        await(runtime.setupMatch());
        await(runtime.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "B"));
        await(runtime.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "C"));
    }

    private static <T> T await(CompletableFuture<T> future)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
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

    private static Game createResettingBoardGame() {
        Player alice = player("Alice", TotemColor.RED);
        return new Game(
                21,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.RESETTING_BOARD),
                new ResetBoardRecordingBoard(),
                Era.first,
                GameStatus.IN_PROGRESS
        );
    }

    private static Player player(String nickname, TotemColor color) {
        return new Player(nickname, 0, 0, color, new Tribe());
    }

    private static final class RecordingSavedMatchService implements SavedMatchService {
        private final List<String> events;
        private int initializeCalls;
        private int turnStartedCalls;
        private int turnFinishedCalls;
        private int completedCalls;
        private RuntimeException initializeFailure;
        private RuntimeException turnStartedFailure;

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
            if (this.initializeFailure != null) {
                throw this.initializeFailure;
            }
        }

        @Override
        public void saveTurnStarted(Game game, String hostNickname) {
            this.turnStartedCalls++;
            this.events.add("save:turnStarted");
            if (this.turnStartedFailure != null) {
                throw this.turnStartedFailure;
            }
        }

        @Override
        public void saveEraChanged(Game game, String hostNickname) {
            this.events.add("save:eraChanged");
        }

        @Override
        public void saveTurnFinished(Game game, String hostNickname) {
            this.turnFinishedCalls++;
            this.events.add("save:turnFinished");
        }

        @Override
        public void completeMatch(Game game, String hostNickname) {
            this.completedCalls++;
            this.events.add("save:completed");
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
            this.events.add("ranking:recorded");
            if (this.failure != null) {
                throw this.failure;
            }
            return new RankingResult(1, List.of(new LeaderboardEntry(requestingNickname, 1_000, 1, 1, 1)));
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
        private ResetBoardRecordingBoard() {
            super(null, null, new ArrayList<>());
        }

        @Override
        public boolean resetBoardAfterRound(Era currentEra) {
            return false;
        }
    }
}
