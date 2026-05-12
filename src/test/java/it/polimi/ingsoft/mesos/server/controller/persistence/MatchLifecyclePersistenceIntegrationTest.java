package it.polimi.ingsoft.mesos.server.controller.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.controller.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.command.ClientQueuedCommand;
import it.polimi.ingsoft.mesos.server.controller.command.EnqueuedMessage;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.MoveTotemPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.AuthenticationPayload;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferSpace;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.game.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.game.TurnState;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.ranking.NoOpRankingService;
import it.polimi.ingsoft.mesos.server.network.ClientConnection;
import it.polimi.ingsoft.mesos.server.persistence.game.FileMatchPersistence;
import it.polimi.ingsoft.mesos.server.persistence.game.MatchPersistence;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;
import it.polimi.ingsoft.mesos.server.persistence.game.file.CheckpointFileLocator;
import it.polimi.ingsoft.mesos.server.persistence.game.mapper.GameSnapshotMapper;
import it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchLifecyclePersistenceIntegrationTest {
    @TempDir
    Path persistenceDirectory;

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-09T10:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void setupAndEventPersistCheckpointInFileStore() throws InterruptedException {
        int gameId = 10;
        MatchPersistence persistence = createPersistence();
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");

        Game game = new InMemoryGameFactory(gameId).createGame(gameId, players);
        GameOrchestrator orchestrator = new GameOrchestrator(
                game,
                players.getFirst().getNickname(),
                null,
                persistence,
                sessions
        );

        orchestrator.setupMatch();
        List<SavedMatchSnapshot> setupSnapshots = persistence.loadActiveMatches();

        assertEquals(1, setupSnapshots.size());
        assertEquals(GameStatus.IN_PROGRESS.name(), setupSnapshots.getFirst().game().status());
        assertEquals(1L, setupSnapshots.getFirst().game().stateVersion());
        assertTrue(Files.exists(new CheckpointFileLocator(this.persistenceDirectory).checkpointFileFor(gameId)));

        String offerTile = pickAvailableOfferSpace(orchestrator);
        Thread worker = startOrchestratorThread(orchestrator);
        try {
            enqueueMoveTotem(sessions, orchestrator, "Alice", gameId, offerTile);
            waitForStateVersion(orchestrator.getGame(), 2L);
        } finally {
            worker.interrupt();
            worker.join(500);
        }

        List<SavedMatchSnapshot> moveSnapshots = persistence.loadActiveMatches();
        assertEquals(1, moveSnapshots.size());
        assertEquals(2L, moveSnapshots.getFirst().game().stateVersion());
    }

    @Test
    void restartRestoresMatchAndRestoredPlayersAreTrackedBySessionRegistry() throws InterruptedException {
        int gameId = 11;
        MatchPersistence persistence = createPersistence();
        ServerSessionRegistry bootstrapSessions = new ServerSessionRegistry();
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        Game originalGame = new InMemoryGameFactory(gameId).createGame(gameId, players);
        GameOrchestrator original = new GameOrchestrator(
                originalGame,
                players.getFirst().getNickname(),
                null,
                persistence,
                bootstrapSessions
        );

        original.setupMatch();
        String offerTile = pickAvailableOfferSpace(original);
        Thread worker = startOrchestratorThread(original);
        try {
            enqueueMoveTotem(bootstrapSessions, original, "Alice", gameId, offerTile);
            waitForStateVersion(original.getGame(), 2L);
        } finally {
            worker.interrupt();
            worker.join(500);
        }

        ServerSessionRegistry restoredSessions = new ServerSessionRegistry();
        RestoredMatchBootstrapper.restoreActiveMatches(
                persistence,
                NoOpRankingService.INSTANCE,
                restoredSessions
        );

        assertTrue(restoredSessions.getGameOrchestrator(gameId) == null);
        assertNotNull(restoredSessions.getLobbyController(gameId));
        assertEquals(LobbyStatus.RESUMABLE, restoredSessions.getLobbyModel(gameId).orElseThrow().getStatus());
        assertTrue(Files.exists(new CheckpointFileLocator(this.persistenceDirectory).checkpointFileFor(gameId)));
    }

    @Test
    void finishedMatchCheckpointIsNotRestoredAndIsDeleted() {
        int gameId = 12;
        MatchPersistence persistence = createPersistence();
        ServerSessionRegistry restoredSessions = new ServerSessionRegistry();

        Game finishedGame = createSimpleFinishedGame(gameId);
        SavedMatchSnapshot finishedSnapshot = new GameSnapshotMapper(FIXED_CLOCK).toSnapshot(
                finishedGame,
                "Alice"
        );
        persistence.save(finishedSnapshot);

        CheckpointFileLocator locator = new CheckpointFileLocator(this.persistenceDirectory);
        assertTrue(Files.exists(locator.checkpointFileFor(gameId)));

        RestoredMatchBootstrapper.restoreActiveMatches(
                persistence,
                NoOpRankingService.INSTANCE,
                restoredSessions
        );

        assertTrue(restoredSessions.getGameOrchestrator(gameId) == null);
        assertTrue(Files.notExists(locator.checkpointFileFor(gameId)));
    }

    @Test
    void corruptedAndInvalidCheckpointDoesNotBlockRestoreOfValidMatch() throws java.io.IOException {
        int corruptedGameId = 13;
        int validGameId = 14;
        MatchPersistence persistence = createPersistence();
        CheckpointFileLocator locator = new CheckpointFileLocator(this.persistenceDirectory);

        Files.writeString(locator.checkpointFileFor(corruptedGameId), "NOT_JSON");

        Game validMatch = createSimpleInProgressGame(validGameId);
        SavedMatchSnapshot validSnapshot = new GameSnapshotMapper(FIXED_CLOCK).toSnapshot(
                validMatch,
                "Alice"
        );
        persistence.save(validSnapshot);

        ServerSessionRegistry restoredSessions = new ServerSessionRegistry();
        RestoredMatchBootstrapper.restoreActiveMatches(
                persistence,
                NoOpRankingService.INSTANCE,
                restoredSessions
        );

        assertNotNull(restoredSessions.getLobbyController(validGameId));
        assertTrue(restoredSessions.getGameOrchestrator(validGameId) == null);
        assertTrue(restoredSessions.getGameOrchestrator(corruptedGameId) == null);
        assertTrue(Files.exists(locator.corruptedFileFor(corruptedGameId)));
        assertTrue(Files.notExists(locator.checkpointFileFor(corruptedGameId)));
    }

    @Test
    void restoredNicknameCanAuthenticateAndSeeResumableLobby() throws InterruptedException {
        int gameId = 15;
        MatchPersistence persistence = createPersistence();
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        ServerSessionRegistry originalSessions = new ServerSessionRegistry();
        Game originalGame = new InMemoryGameFactory(gameId).createGame(gameId, players);
        GameOrchestrator original = new GameOrchestrator(
                originalGame,
                players.getFirst().getNickname(),
                null,
                persistence,
                originalSessions
        );

        original.setupMatch();
        String offerTile = pickAvailableOfferSpace(original);
        Thread setupWorker = startOrchestratorThread(original);
        try {
            enqueueMoveTotem(originalSessions, original, "Alice", gameId, offerTile);
            waitForStateVersion(original.getGame(), 2L);
        } finally {
            setupWorker.interrupt();
            setupWorker.join(500);
        }

        ServerSessionRegistry sessions = new ServerSessionRegistry();
        RestoredMatchBootstrapper.restoreActiveMatches(
                persistence,
                NoOpRankingService.INSTANCE,
                sessions
        );

        ServerController controller = new ServerController(DefaultGameFactory.getInstance(), NoOpRankingService.INSTANCE, MatchPersistence.disabled(), sessions);
        RecordingConnection aliceConnection = new RecordingConnection();
        String aliceSessionId = controller.registerConnection(aliceConnection);

        Thread controllerThread = new Thread(controller, "test-controller-worker");
        controllerThread.setDaemon(true);
        controllerThread.start();
        try {
            controller.enqueueMessage(new EnqueuedMessage(aliceSessionId,
                    new ClientQueuedCommand(new AuthenticationPayload("Alice"))));
            waitForPhase(sessions, aliceSessionId, ServerSessionRegistry.Phase.PRE_LOBBY);
        } finally {
            controllerThread.interrupt();
            controllerThread.join(500);
        }

        assertEquals(ServerSessionRegistry.Phase.PRE_LOBBY, sessions.getPhaseOf(aliceSessionId));
        assertNotNull(sessions.getLobbyController(gameId));
        assertEquals(LobbyStatus.RESUMABLE, sessions.getLobbyModel(gameId).orElseThrow().getStatus());
    }

    private static Thread startOrchestratorThread(GameOrchestrator orchestrator) {
        Thread t = new Thread(orchestrator, "test-game-worker");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void enqueueMoveTotem(ServerSessionRegistry sessions, GameOrchestrator orchestrator,
                                          String nickname, int gameId, String tile) {
        String sessionId = sessions.registerConnection(new RecordingConnection());
        sessions.authenticate(sessionId, nickname);
        sessions.enterGame(sessionId, gameId);
        orchestrator.enqueueMessage(new EnqueuedMessage(sessionId,
                new ClientQueuedCommand(new MoveTotemPayload(tile))));
    }

    private static void waitForStateVersion(Game game, long expected) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            if (game.getStateVersion() >= expected) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Timeout waiting for game stateVersion >= " + expected);
    }

    private static void waitForPhase(ServerSessionRegistry sessions, String sessionId,
                                      ServerSessionRegistry.Phase expected) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            if (sessions.getPhaseOf(sessionId) == expected) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Timeout waiting for session phase " + expected);
    }

    private static void waitForUpdates(RecordingConnection connection, int baseline) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            if (connection.updates.size() > baseline) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Timeout waiting for connection updates > " + baseline);
    }

    private MatchPersistence createPersistence() {
        return new FileMatchPersistence(
                true,
                this.persistenceDirectory,
                new ObjectMapper(),
                FIXED_CLOCK
        );
    }

    private Game createSimpleFinishedGame(int gameId) {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        return new Game(
                gameId,
                players,
                new TurnState(),
                new TurnOrderTrack(players, 0, 1, RoundPhase.PLACING_TOTEMS),
                createSimpleBoard(players),
                Era.first,
                GameStatus.FINISHED,
                7L
        );
    }

    private Game createSimpleInProgressGame(int gameId) {
        return this.createSimpleGame(gameId, GameStatus.IN_PROGRESS, 1L);
    }

    private Game createSimpleGame(int gameId, GameStatus status, long stateVersion) {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        return new Game(
                gameId,
                players,
                new TurnState(),
                new TurnOrderTrack(players, 0, 1, RoundPhase.PLACING_TOTEMS),
                createSimpleBoard(players),
                Era.first,
                status,
                stateVersion
        );
    }

    private Board createSimpleBoard(List<Player> players) {
        return new Board(
                TribeDeck.fromOrderedCards(new ArrayList<>()),
                BuildingDeck.fromOrderedCards(new ArrayList<>()),
                players
        );
    }

    private String pickAvailableOfferSpace(GameOrchestrator orchestrator) {
        List<OfferSpace> available = orchestrator.getGame().getBoard().getAvailableOfferSpaces();
        return available.get(0).getCode();
    }

    private static final class InMemoryGameFactory implements GameFactory {
        private final int gameId;

        private InMemoryGameFactory(int gameId) {
            this.gameId = gameId;
        }

        @Override
        public Game createGame(List<Player> players) {
            return this.createGame(this.gameId, players);
        }

        @Override
        public Game createGame(int gameId, List<Player> players) {
            return new Game(
                    gameId,
                    players,
                    new TurnState(),
                    new TurnOrderTrack(),
                    new Board(
                            TribeDeck.fromOrderedCards(new ArrayList<>()),
                            BuildingDeck.fromOrderedCards(new ArrayList<>()),
                            players
                    )
            );
        }
    }

    private static final class RecordingConnection implements ClientConnection, Listener {
        private final List<Object> updates = new ArrayList<>();

        @Override
        public void sendPing() {
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public void notifyUpdate(Object payload) {
            this.updates.add(payload);
        }

        private Object lastUpdate() {
            return this.updates.getLast();
        }
    }
}
