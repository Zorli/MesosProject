package it.polimi.ingsoft.mesos.server.controller.GameOrchestrator;

import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.game.TurnState;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.ranking.LeaderboardEntry;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchHistory;
import it.polimi.ingsoft.mesos.server.model.ranking.MatchScore;
import it.polimi.ingsoft.mesos.server.model.ranking.NoOpRankingService;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingResult;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.server.controller.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.controller.command.ClientQueuedCommand;
import it.polimi.ingsoft.mesos.server.controller.command.EnqueuedMessage;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.network.ClientConnection;
import it.polimi.ingsoft.mesos.server.persistence.game.MatchPersistence;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;
import it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.ChooseCardPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.ExtraDrawPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.MoveTotemPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameOrchestratorRankingTest {

    @Test
    void constructorExposesGameAndImmutablePlayersList() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        TrackingGame game = new TrackingGame(players);
        TrackingGameFactory gameFactory = new TrackingGameFactory(game);

        GameOrchestrator orchestrator = newOrchestrator(gameFactory, players, "Alice", null, null, null);

        assertEquals(game, orchestrator.getGame());
        assertEquals(players.size(), orchestrator.getPlayers().size());
        assertEquals(players.getFirst().getNickname(), orchestrator.getPlayers().getFirst().getNickname());
        assertThrows(UnsupportedOperationException.class, () -> orchestrator.getPlayers().add(new Player("Charlie", 0, 0, TotemColor.YELLOW)));
        assertTrue(orchestrator.getFinalRankingResult().isEmpty());
        assertFalse(orchestrator.isFrozen());
    }

    @Test
    void constructorValidatesInputPreconditions() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        TrackingGameFactory validFactory = new TrackingGameFactory(new TrackingGame(players));

        assertThrows(NullPointerException.class, () -> newOrchestrator(null, players, "Alice", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> newOrchestrator(validFactory, null, "Alice", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> newOrchestrator(validFactory, new ArrayList<>(), "Alice", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> newOrchestrator(validFactory, players, "", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> newOrchestrator(validFactory, players, "   ", null, null, null));

        ArrayList<Player> invalidHostPlayers = new ArrayList<>();
        invalidHostPlayers.add(new Player(null, 0, 0, TotemColor.RED));
        assertThrows(IllegalArgumentException.class, () -> newOrchestrator(validFactory, invalidHostPlayers, null, null, null, null));
    }

    @Test
    void constructorValidatesPlayersNullOrEmpty() {
        GameFactory singlePlayerFactory = new TrackingGameFactory(new TrackingGame(MesosTestFixtures.createPlayers("Alice")));

        assertThrows(IllegalArgumentException.class, () -> newOrchestrator(singlePlayerFactory, null, "Alice", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> newOrchestrator(singlePlayerFactory, new ArrayList<>(), "Alice", null, null, null));

        ArrayList<Player> validPlayers = MesosTestFixtures.createPlayers("Alice", "Bob");
        GameFactory validFactory = new TrackingGameFactory(new TrackingGame(validPlayers));
        GameOrchestrator orchestrator = newOrchestrator(validFactory, validPlayers, "Alice", null, null, null);
        assertEquals(validPlayers, orchestrator.getPlayers());
    }

    @Test
    void hostValidationCoversNullOrBlankNicknameInConstructor() {
        GameFactory gameFactory = new TrackingGameFactory(new TrackingGame(MesosTestFixtures.createPlayers("Alice")));
        ArrayList<Player> validPlayers = MesosTestFixtures.createPlayers("Alice", "Bob");

        assertThrows(
                IllegalArgumentException.class,
                () -> newOrchestrator(gameFactory, validPlayers, null, null, null, null)
        );

        ArrayList<Player> playersWithBlankHost = new ArrayList<>();
        playersWithBlankHost.add(new Player("  ", 0, 0, TotemColor.RED));
        assertThrows(
                IllegalArgumentException.class,
                () -> newOrchestrator(gameFactory, playersWithBlankHost, "  ", null, null, null)
        );
    }

    @Test
    void setupMatchForwardsToGameAndRecordsRankingOnlyWhenFinished() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        players.getFirst().gainPrestigePoints(10);
        players.get(1).gainPrestigePoints(4);
        RecordingRankingService rankingService = new RecordingRankingService();

        TrackingGameFactory gameFactory = new TrackingGameFactory(
                new TrackingGame(players, GameStatus.IN_PROGRESS, true, false, false, false)
        );
        GameOrchestrator orchestrator = newOrchestrator(
                gameFactory,
                players,
                "Alice",
                rankingService,
                null,
                null
        );

        orchestrator.setupMatch();

        assertEquals(1, gameFactory.getGame().getSetupCalls());
        assertEquals(1, rankingService.recordCalls);
        assertEquals(String.valueOf(orchestrator.getGame().getId()), rankingService.lastGameId);
        assertEquals(2, rankingService.lastPlayerCount);
        assertEquals("Alice", rankingService.requestingNickname);
        assertEquals(
                List.of(new MatchScore("Alice", 10), new MatchScore("Bob", 4)),
                rankingService.lastFinalScores
        );
        assertTrue(orchestrator.getFinalRankingResult().isPresent());
    }

    @Test
    void setupAndGameplayCommandsForwardToGameForKnownPlayerWithoutRankingWhenNotFinished() throws InterruptedException {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        TrackingGame game = new TrackingGame(players, GameStatus.SETUP, false, false, false, false);
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        GameOrchestrator orchestrator = newOrchestrator(
                new TrackingGameFactory(game),
                players,
                "Alice",
                null,
                sessions,
                null
        );

        orchestrator.setupMatch();

        String aliceSessionId = registerConnectedPlayer(sessions, "Alice", game.getId());
        String bobSessionId = registerConnectedPlayer(sessions, "Bob", game.getId());
        Thread worker = new Thread(orchestrator::run);
        worker.setDaemon(true);
        worker.start();
        try {
            orchestrator.enqueueMessage(new EnqueuedMessage(aliceSessionId, new ClientQueuedCommand(new MoveTotemPayload("A1"))));
            orchestrator.enqueueMessage(new EnqueuedMessage(bobSessionId, new ClientQueuedCommand(new ChooseCardPayload(7))));
            orchestrator.enqueueMessage(new EnqueuedMessage(aliceSessionId, new ClientQueuedCommand(new ExtraDrawPayload(3))));
            waitForCondition(() -> game.getExtraDrawnCalls() == 1, 20, 50L);
        } finally {
            worker.interrupt();
            worker.join(200);
        }

        assertEquals(1, game.getSetupCalls());
        assertEquals(1, game.getMoveTotemCalls());
        assertEquals(1, game.getChooseCardCalls());
        assertEquals(1, game.getExtraDrawnCalls());
        assertEquals(players.getFirst(), game.getLastActionPlayer());
        assertEquals("A1", game.getLastTile());
        assertEquals(3, game.getLastCardId());
        assertEquals(2, game.getRecordedRankingCalls().size());
    }

    @Test
    void unknownPlayerCannotResolveActionAcrossAllMainFlowMethods() throws InterruptedException {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        TrackingGame game = new TrackingGame(players);
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        GameOrchestrator orchestrator = newOrchestrator(game, "Alice", null, null, sessions);

        String eveSessionId = registerConnectedPlayer(sessions, "Eve", game.getId());
        Thread worker = new Thread(orchestrator::run);
        worker.setDaemon(true);
        worker.start();
        try {
            orchestrator.enqueueMessage(new EnqueuedMessage(eveSessionId, new ClientQueuedCommand(new MoveTotemPayload("A1"))));
            orchestrator.enqueueMessage(new EnqueuedMessage(eveSessionId, new ClientQueuedCommand(new ChooseCardPayload(1))));
            orchestrator.enqueueMessage(new EnqueuedMessage(eveSessionId, new ClientQueuedCommand(new ExtraDrawPayload(1))));
            Thread.sleep(200);
        } finally {
            worker.interrupt();
            worker.join(200);
        }

        assertEquals(0, game.getMoveTotemCalls());
        assertEquals(0, game.getChooseCardCalls());
        assertEquals(0, game.getExtraDrawnCalls());
    }

    @Test
    void rankingShouldNotRunWhenNoServiceProvidedOrGameIsNotFinished() throws InterruptedException {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        TrackingGame noServiceGame = new TrackingGame(players, GameStatus.SETUP, false, true, false, false);
        GameOrchestrator orchestratorNoService = newOrchestrator(
                new TrackingGameFactory(noServiceGame),
                players,
                "Alice",
                null,
                sessions,
                null
        );
        String aliceSessionId = registerConnectedPlayer(sessions, "Alice", noServiceGame.getId());
        Thread worker = new Thread(orchestratorNoService::run);
        worker.setDaemon(true);
        worker.start();
        orchestratorNoService.enqueueMessage(new EnqueuedMessage(aliceSessionId, new ClientQueuedCommand(new ChooseCardPayload(1))));
        worker.join(5000);
        assertTrue(orchestratorNoService.getFinalRankingResult().isEmpty());

        RecordingRankingService rankingService = new RecordingRankingService();
        GameOrchestrator orchestratorNoFinish = newOrchestrator(
                new TrackingGameFactory(new TrackingGame(players, GameStatus.SETUP, false, false, false, false)),
                players,
                "Alice",
                rankingService,
                null,
                null
        );
        orchestratorNoFinish.setupMatch();

        assertEquals(0, rankingService.recordCalls);
        assertTrue(orchestratorNoFinish.getFinalRankingResult().isEmpty());
    }

    @Test
    void noDbRankingServiceDoesNotFreezeMatchAndProducesNoRankingResult() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        TrackingGame game = new TrackingGame(players, GameStatus.IN_PROGRESS, true, false, false, false);
        GameOrchestrator orchestrator = newOrchestrator(
                new TrackingGameFactory(game),
                players,
                "Alice",
                NoOpRankingService.INSTANCE,
                null,
                null
        );

        orchestrator.setupMatch();

        assertFalse(orchestrator.isFrozen());
        assertTrue(orchestrator.getFinalRankingResult().isEmpty());
    }

    @Test
    void rankingServiceRecordsOnlyOnceEvenWhenMoreCommandsArrive() throws InterruptedException {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        TrackingGame game = new TrackingGame(players, GameStatus.IN_PROGRESS, false, false, false, true);
        TrackingGameFactory gameFactory = new TrackingGameFactory(game);
        RecordingRankingService rankingService = new RecordingRankingService();
        GameOrchestrator orchestrator = newOrchestrator(gameFactory, players, "Alice", rankingService, sessions, null);

        orchestrator.setupMatch();

        String aliceSessionId = registerConnectedPlayer(sessions, "Alice", game.getId());
        String bobSessionId = registerConnectedPlayer(sessions, "Bob", game.getId());
        Thread worker = new Thread(orchestrator::run);
        worker.setDaemon(true);
        worker.start();
        orchestrator.enqueueMessage(new EnqueuedMessage(bobSessionId, new ClientQueuedCommand(new MoveTotemPayload("A1"))));
        orchestrator.enqueueMessage(new EnqueuedMessage(aliceSessionId, new ClientQueuedCommand(new ExtraDrawPayload(2))));
        worker.join(5000);

        assertEquals(1, rankingService.recordCalls);
        assertEquals(1, gameFactory.getGame().getSetupCalls());
        assertEquals(1, gameFactory.getGame().getMoveTotemCalls());
        assertEquals(0, gameFactory.getGame().getChooseCardCalls());
        assertEquals(1, gameFactory.getGame().getExtraDrawnCalls());
    }

    @Test
    void persistStateIsSkippedWhenStateVersionDoesNotChange() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        RecordingMatchPersistence persistence = new RecordingMatchPersistence();
        GameOrchestrator orchestrator = newOrchestrator(
                new TrackingGameFactory(new TrackingGame(players)),
                players,
                "Alice",
                null,
                new ServerSessionRegistry(),
                persistence
        );

        orchestrator.setupMatch();

        assertEquals(0, persistence.getSaveCount());
        assertEquals(0, persistence.getSavedSnapshots().size());
    }

    @Test
    void persistStateIsPerformedWhenVersionChanges() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        TrackingVersionedGame game = new TrackingVersionedGame(players);
        RecordingMatchPersistence persistence = new RecordingMatchPersistence();
        GameOrchestrator orchestrator = newOrchestrator(
                new TrackingGameFactory(game),
                players,
                "Alice",
                null,
                new ServerSessionRegistry(),
                persistence
        );

        orchestrator.setupMatch();

        assertEquals(1, persistence.getSaveCount());
        assertEquals(1, persistence.getSavedSnapshots().size());
    }

    @Test
    void saveFailureOnMutatingCommandFreezesMatchAndNotifiesCheckpointError() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        TrackingVersionedGame game = new TrackingVersionedGame(players);
        GameOrchestrator orchestrator = newOrchestrator(
                new TrackingGameFactory(game),
                players,
                "Alice",
                null,
                new ServerSessionRegistry(),
                new FailingMatchPersistence(new IllegalStateException("disk full"))
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                orchestrator::setupMatch
        );
        assertTrue(exception.getMessage().contains("Unable to persist match"));
        assertTrue(orchestrator.isFrozen());
        ErrorPayload error = game.getLastNotifiedError();
        assertEquals("CHECKPOINT_ERROR", error.code);
        assertTrue(error.message.contains("setupMatch"));
    }

    @Test
    void queuedCommandSaveFailureFreezesMatchAndStopsFurtherStateChanges() throws InterruptedException {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        MutatingVersionedTrackingGame game = new MutatingVersionedTrackingGame(players);
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        GameOrchestrator orchestrator = newOrchestrator(
                new TrackingGameFactory(game),
                players,
                "Alice",
                null,
                sessions,
                new FailingMatchPersistence(new IllegalStateException("disk full"))
        );

        String sessionId = registerConnectedPlayer(sessions, "Alice", 7);
        Thread worker = new Thread(orchestrator::run);
        worker.start();
        try {
            orchestrator.enqueueMessage(new EnqueuedMessage(sessionId, new ClientQueuedCommand(new ChooseCardPayload(1))));
            waitForCondition(() -> orchestrator.isFrozen(), 20, 50L);

            assertTrue(orchestrator.isFrozen());
            assertEquals("CHECKPOINT_ERROR", game.getLastNotifiedError().code);
            assertEquals(1, game.getChooseCardCalls());

            orchestrator.enqueueMessage(new EnqueuedMessage(sessionId, new ClientQueuedCommand(new ChooseCardPayload(2))));
            Thread.sleep(120);
            assertEquals(1, game.getChooseCardCalls());
        } finally {
            worker.interrupt();
            worker.join(200);
        }
    }

    @Test
    void finishedMatchDeletesSnapshotAfterRankingAndBeforeCleanup() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        TrackingGame game = new TrackingGame(players, GameStatus.IN_PROGRESS, true, false, false, false);
        RecordingMatchPersistence persistence = new RecordingMatchPersistence();
        GameOrchestrator orchestrator = newOrchestrator(
                new TrackingGameFactory(game),
                players,
                "Alice",
                NoOpRankingService.INSTANCE,
                new ServerSessionRegistry(),
                persistence
        );

        orchestrator.setupMatch();

        assertEquals(1, persistence.getDeleteCount());
        assertEquals(1, persistence.getSaveCount());
    }

    @Test
    void freezeForDisconnectedPlayerCapturesErrorAndBlocksFutureCommands() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        TrackingGame game = new TrackingGame(players);
        GameOrchestrator orchestrator = newOrchestrator(game, "Alice", null, null, null);

        orchestrator.freezeForDisconnectedPlayer("Alice");

        ErrorPayload lastPayload = game.getLastNotifiedError();
        assertEquals("PLAYER_DISCONNECTED", lastPayload.code);
        assertTrue(lastPayload.message.contains("Alice"));
        assertTrue(orchestrator.isFrozen());
        assertThrows(MatchFrozenException.class, () -> orchestrator.setupMatch());
    }

    @Test
    void rankingFinalizationFailureFreezesMatchAndThrowsDomainException() throws InterruptedException {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        TrackingGame game = new TrackingGame(players, GameStatus.IN_PROGRESS, false, false, false, true);
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        GameOrchestrator orchestrator = newOrchestrator(
                new TrackingGameFactory(game),
                players,
                "Alice",
                new FailingRankingService(),
                sessions,
                null
        );

        String aliceSessionId = registerConnectedPlayer(sessions, "Alice", game.getId());
        Thread worker = new Thread(orchestrator::run);
        worker.setDaemon(true);
        worker.start();
        try {
            orchestrator.enqueueMessage(new EnqueuedMessage(aliceSessionId, new ClientQueuedCommand(new ExtraDrawPayload(2))));
            waitForCondition(orchestrator::isFrozen, 20, 50L);
        } finally {
            worker.interrupt();
            worker.join(200);
        }

        assertTrue(orchestrator.isFrozen());
        assertThrows(MatchFrozenException.class, () -> orchestrator.setupMatch());
    }

    private static GameOrchestrator newOrchestrator(
            Game game,
            String hostNickname,
            RankingService rankingService,
            MatchPersistence matchPersistence,
            ServerSessionRegistry sessions
    ) {
        return new GameOrchestrator(game, hostNickname, rankingService, matchPersistence, sessions);
    }

    private static GameOrchestrator newOrchestrator(
            GameFactory gameFactory,
            ArrayList<Player> players,
            String hostNickname,
            RankingService rankingService,
            ServerSessionRegistry sessions,
            MatchPersistence matchPersistence
    ) {
        if (gameFactory == null) {
            throw new NullPointerException("Game factory cannot be null");
        }
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("Players cannot be null or empty");
        }
        return new GameOrchestrator(
                gameFactory.createGame(players),
                hostNickname,
                rankingService,
                matchPersistence,
                sessions
        );
    }

    private static final class TrackingGameFactory implements GameFactory {
        private final TrackingGame game;

        private TrackingGameFactory(TrackingGame game) {
            this.game = game;
        }

        @Override
        public Game createGame(List<Player> players) {
            return this.game;
        }

        @Override
        public Game createGame(int gameId, List<Player> players) {
            return this.game;
        }

        private TrackingGame getGame() {
            return this.game;
        }
    }

    private static final class TrackingVersionedGame extends TrackingGame {
        private TrackingVersionedGame(List<Player> players) {
            super(players, GameStatus.SETUP, false, false, false, false);
        }

        @Override
        public void setup() {
            super.setup();
            this.setStatus(GameStatus.IN_PROGRESS);
        }
    }

    private static final class MutatingVersionedTrackingGame extends TrackingGame {
        private int chooseCardCalls;

        private MutatingVersionedTrackingGame(List<Player> players) {
            super(players, GameStatus.SETUP, false, false, false, false);
        }

        @Override
        public void chooseCard(Player player, int cardId) {
            this.chooseCardCalls++;
            super.chooseCard(player, cardId);
            this.setStatus(this.getStatus());
        }

        private int getChooseCardCalls() {
            return this.chooseCardCalls;
        }
    }

    private static String registerConnectedPlayer(ServerSessionRegistry sessions, String nickname, int gameId)
            throws IllegalStateException {
        String sessionId = sessions.registerConnection(new RecordingGameConnection());
        sessions.authenticate(sessionId, nickname);
        sessions.enterGame(sessionId, gameId);
        return sessionId;
    }

    private static void waitForCondition(java.util.function.BooleanSupplier condition, int maxAttempts, long sleepMillis)
            throws InterruptedException {
        for (int i = 0; i < maxAttempts; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(sleepMillis);
        }
    }

    private static final class RecordingGameConnection implements ClientConnection, Listener {
        @Override
        public void sendPing() {
        }

        @Override
        public void notifyUpdate(Object payload) {
        }

        @Override
        public void close() throws IOException {
        }
    }

    private static class RecordingMatchPersistence implements MatchPersistence {
        private final List<SavedMatchSnapshot> savedSnapshots = new ArrayList<>();
        private int saveCount;
        private int deleteCount;

        @Override
        public java.util.List<SavedMatchSnapshot> loadActiveMatches() {
            return List.of();
        }

        @Override
        public void save(SavedMatchSnapshot snapshot) {
            this.savedSnapshots.add(snapshot);
            this.saveCount++;
        }

        @Override
        public void delete(int gameId) {
            this.deleteCount++;
        }

        private int getSaveCount() {
            return this.saveCount;
        }

        private int getDeleteCount() {
            return this.deleteCount;
        }

        private List<SavedMatchSnapshot> getSavedSnapshots() {
            return this.savedSnapshots;
        }
    }

    private static final class FailingMatchPersistence extends RecordingMatchPersistence {
        private final RuntimeException failure;

        private FailingMatchPersistence(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void save(SavedMatchSnapshot snapshot) {
            throw this.failure;
        }
    }

    private static class TrackingGame extends Game {
        private int setupCalls;
        private int moveTotemCalls;
        private int chooseCardCalls;
        private int extraDrawnCalls;
        private Player lastActionPlayer;
        private String lastTile;
        private int lastCardId;
        private ErrorPayload lastNotifiedError;

        private final boolean finishOnSetup;
        private final boolean finishOnMove;
        private final boolean finishOnChoose;
        private final boolean finishOnExtraDrawn;
        private final List<String> recordedRankingCalls = new ArrayList<>();

        private TrackingGame(List<Player> players) {
            this(players, GameStatus.SETUP, false, false, false, false);
        }

        private TrackingGame(List<Player> players,
                             GameStatus initialStatus,
                             boolean finishOnSetup,
                             boolean finishOnMove,
                             boolean finishOnChoose,
                             boolean finishOnExtraDrawn) {
            super(
                    7,
                    players,
                    new TurnState(),
                    new it.polimi.ingsoft.mesos.server.model.game.TurnOrderTrack(
                            new ArrayList<>(players), 0, 0,
                            it.polimi.ingsoft.mesos.server.model.game.RoundPhase.PLACING_TOTEMS),
                    new Board(
                            TribeDeck.fromOrderedCards(new ArrayList<>()),
                            BuildingDeck.fromOrderedCards(new ArrayList<>()),
                            players
                    )
            );
            this.setStatus(initialStatus);
            this.finishOnSetup = finishOnSetup;
            this.finishOnMove = finishOnMove;
            this.finishOnChoose = finishOnChoose;
            this.finishOnExtraDrawn = finishOnExtraDrawn;
        }

        @Override
        public void setup() {
            this.setupCalls++;
            if (this.finishOnSetup) {
                this.setStatus(GameStatus.FINISHED);
            }
        }

        @Override
        public void moveTotem(Player player, String tile) {
            this.moveTotemCalls++;
            this.lastActionPlayer = player;
            this.lastTile = tile;
            if (this.finishOnMove) {
                this.setStatus(GameStatus.FINISHED);
            }
        }

        @Override
        public void chooseCard(Player player, int cardId) {
            this.chooseCardCalls++;
            this.lastActionPlayer = player;
            this.lastCardId = cardId;
            this.recordedRankingCalls.add("chooseCard");
            if (this.finishOnChoose) {
                this.setStatus(GameStatus.FINISHED);
            }
        }

        @Override
        public void extraDrawn(Player player, int cardId) {
            this.extraDrawnCalls++;
            this.lastActionPlayer = player;
            this.lastCardId = cardId;
            this.recordedRankingCalls.add("extraDrawn");
            if (this.finishOnExtraDrawn) {
                this.setStatus(GameStatus.FINISHED);
            }
        }

        @Override
        public void notifyError(String code, String message) {
            this.lastNotifiedError = new ErrorPayload(code, message);
        }

        private int getSetupCalls() {
            return this.setupCalls;
        }

        private int getMoveTotemCalls() {
            return this.moveTotemCalls;
        }

        private int getChooseCardCalls() {
            return this.chooseCardCalls;
        }

        private int getExtraDrawnCalls() {
            return this.extraDrawnCalls;
        }

        private Player getLastActionPlayer() {
            return this.lastActionPlayer;
        }

        private String getLastTile() {
            return this.lastTile;
        }

        private int getLastCardId() {
            return this.lastCardId;
        }

        private List<String> getRecordedRankingCalls() {
            return this.recordedRankingCalls;
        }

        protected ErrorPayload getLastNotifiedError() {
            return this.lastNotifiedError;
        }
    }

    private static class RecordingRankingServiceAdapter implements RankingService {
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
            throw new UnsupportedOperationException("Override in concrete service");
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

    private static final class RecordingRankingService extends RecordingRankingServiceAdapter {
        private int recordCalls;
        private String lastGameId;
        private int lastPlayerCount;
        private String requestingNickname;
        private List<MatchScore> lastFinalScores = List.of();

        @Override
        public RankingResult recordMatchAndGetRanking(String gameId,
                                                      int playerCount,
                                                      Instant playedAtUtc,
                                                      String requestingNickname,
                                                      List<MatchScore> finalScores) {
            this.recordCalls++;
            this.lastGameId = gameId;
            this.lastPlayerCount = playerCount;
            this.requestingNickname = requestingNickname;
            this.lastFinalScores = List.copyOf(finalScores);
            return new RankingResult(
                    1,
                    List.of(new LeaderboardEntry(requestingNickname, 1200, 0, 0, 1))
            );
        }
    }

    private static final class FailingRankingService extends RecordingRankingServiceAdapter {
        @Override
        public RankingResult recordMatchAndGetRanking(String gameId,
                                                      int playerCount,
                                                      Instant playedAtUtc,
                                                      String requestingNickname,
                                                      List<MatchScore> finalScores) {
            throw new IllegalStateException("ranking backend unavailable");
        }
    }
}
