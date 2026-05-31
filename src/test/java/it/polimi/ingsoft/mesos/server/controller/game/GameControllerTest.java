package it.polimi.ingsoft.mesos.server.controller.game;

import it.polimi.ingsoft.mesos.server.controller.command.ClientPayloadCommand;
import it.polimi.ingsoft.mesos.server.controller.command.SystemCloseGameCommand;
import it.polimi.ingsoft.mesos.server.controller.command.SystemDisconnectCommand;
import it.polimi.ingsoft.mesos.server.controller.command.SystemJoinCommand;
import it.polimi.ingsoft.mesos.server.controller.command.SystemSetupMatchCommand;
import it.polimi.ingsoft.mesos.server.helper.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.model.game.board.Board;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.Card;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.game.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.server.network.interfaces.ClientConnection;
import it.polimi.ingsoft.mesos.server.network.interfaces.Listener;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.ChooseCardPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.ExtraDrawPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.GameClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.MoveTotemPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ServerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link GameController}.
 *
 * <p>The controller is tested as an orchestrator: the tests check that queued
 * commands are translated into the correct observable calls on the game model
 * and into the correct registry/listener side effects. The real game rules are
 * intentionally not exercised here because they belong to {@link Game}.
 */
class GameControllerTest {

    /**
     * The controller needs a real game, at least one player, and a non-blank
     * host nickname. These are construction-time requirements, so invalid input
     * should fail immediately instead of producing a half-valid controller.
     */
    @Test
    void constructorRejectsInvalidRequiredData() {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        RecordingGame game = new RecordingGame(players());

        assertThrows(NullPointerException.class, () -> new GameController(null, "Alice", sessions));
        assertThrows(IllegalArgumentException.class, () -> new GameController(new RecordingGame(List.of()), "Alice", sessions));
        assertThrows(IllegalArgumentException.class, () -> new GameController(game, " ", sessions));
    }

    /**
     * The controller stores a stable player list when it is created. External
     * callers can read that list, but must not be able to mutate the
     * controller's match participants through the returned value.
     */
    @Test
    void exposesAnImmutableSnapshotOfGamePlayers() {
        RecordingGame game = new RecordingGame(players());
        GameController controller = new GameController(game, "Alice", new ServerSessionRegistry());

        assertEquals(game, controller.getGame());
        assertEquals(List.of("Alice", "Bob"), controller.getPlayers().stream().map(Player::getNickname).toList());
        assertThrows(UnsupportedOperationException.class, () -> controller.getPlayers().add(player("Carol", TotemColor.WHITE)));
    }

    /**
     * Setup is an internal system command. The controller must execute it on the
     * worker thread and delegate the actual setup rules to the game model.
     */
    @Test
    void setupCommandDelegatesSetupToTheGameModel() throws Exception {
        RecordingGame game = new RecordingGame(players());
        GameController controller = new GameController(game, "Alice", new ServerSessionRegistry());

        Thread worker = start(controller);
        controller.enqueueMessage(new SystemSetupMatchCommand());

        await(() -> game.setupCalled);
        stop(worker);

        assertTrue(game.setupCalled);
    }

    /**
     * Client payloads arrive wrapped in {@link ClientPayloadCommand}. The
     * controller must resolve the acting session to the corresponding player
     * and delegate the command to the matching high-level method on the game
     * model.
     */
    @Test
    void routesKnownClientPayloadsToTheGameModel() throws Exception {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        RecordingGame game = new RecordingGame(players());
        GameController controller = new GameController(game, "Alice", sessions);
        String sessionId = authenticatedGameSession(sessions, "Alice", game.getId(), new DummyConnection());

        Thread worker = start(controller);
        controller.enqueueMessage(new ClientPayloadCommand(sessionId, new MoveTotemPayload("B")));
        controller.enqueueMessage(new ClientPayloadCommand(sessionId, new ChooseCardPayload(7)));
        controller.enqueueMessage(new ClientPayloadCommand(sessionId, new ExtraDrawPayload(9)));

        await(() -> game.actions.size() == 3);
        stop(worker);

        assertEquals(List.of("moveTotem:Alice:B", "chooseCard:Alice:7", "extraDrawn:Alice:9"), game.actions);
    }

    /**
     * Gameplay commands from a session that can no longer be resolved are
     * ignored. This keeps messages from removed sessions from stopping the game worker.
     */
    @Test
    void gameplayCommandFromUnknownSessionIsIgnored() throws Exception {
        RecordingGame game = new RecordingGame(players());
        GameController controller = new GameController(game, "Alice", new ServerSessionRegistry());

        Thread worker = start(controller);
        controller.enqueueMessage(new ClientPayloadCommand("missing-session", new MoveTotemPayload("B")));
        Thread.sleep(50);
        stop(worker);

        assertTrue(game.actions.isEmpty());
    }

    /**
     * If a payload reaches the game controller but is not one of the accepted
     * game commands, the controller reports a phase/action error to that
     * session's listener. This preserves the currently observed behavior without
     * testing the internal switch structure.
     */
    @Test
    void unexpectedClientPayloadReportsPhaseErrorToTheSessionListener() throws Exception {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        RecordingGame game = new RecordingGame(players());
        GameController controller = new GameController(game, "Alice", sessions);
        DummyConnection connection = new DummyConnection();
        String sessionId = authenticatedGameSession(sessions, "Alice", game.getId(), connection);

        Thread worker = start(controller);
        controller.enqueueMessage(new ClientPayloadCommand(sessionId, new UnknownGamePayload()));

        await(() -> !connection.receivedPayloads.isEmpty());
        stop(worker);

        ErrorPayload payload = (ErrorPayload) connection.receivedPayloads.getFirst();
        assertEquals("PHASE_ERROR", payload.code);
        assertEquals("phase-error", payload.message);
    }

    /**
     * A real disconnect removes the old listener, marks the domain player as
     * disconnected, and unregisters the old session from the registry.
     */
    @Test
    void realDisconnectUpdatesGameAndRegistry() throws Exception {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        RecordingGame game = new RecordingGame(players());
        GameController controller = new GameController(game, "Alice", sessions);
        DummyConnection connection = new DummyConnection();
        String sessionId = authenticatedGameSession(sessions, "Alice", game.getId(), connection);

        Thread worker = start(controller);
        controller.enqueueMessage(new SystemDisconnectCommand(sessionId));

        await(() -> game.disconnectedPlayers.size() == 1);
        stop(worker);

        assertEquals("Alice", game.disconnectedPlayers.getFirst().getNickname());
        assertTrue(game.removedListeners.contains(connection));
        assertEquals(game.getId(), sessions.reconnectGameIdOf("Alice"));
        assertThrows(RuntimeException.class, () -> sessions.nicknameOf(sessionId));
    }

    /**
     * The last-player timer does not close the game directly from the timer
     * thread. Its task enqueues a close command, which the game worker later
     * processes in order.
     */
    @Test
    void lastPlayerTimerTaskEnqueuesCloseCommand() throws Exception {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        RecordingGame game = new RecordingGame(players());
        GameController controller = new GameController(game, "Alice", sessions);
        String bobSessionId = authenticatedGameSession(sessions, "Bob", game.getId(), new DummyConnection());

        Thread worker = start(controller);
        controller.enqueueMessage(new SystemDisconnectCommand(bobSessionId));
        await(() -> lastPlayerTimerTask(controller) != null);

        lastPlayerTimerTask(controller).run();

        await(() -> game.closed);
        worker.join(1000);

        assertTrue(game.closed);
    }

    /**
     * Reconnect commands move the new session into the game, add its listener,
     * and mark the existing domain player as connected again.
     */
    @Test
    void reconnectCommandAddsListenerAndReconnectsPlayer() throws Exception {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        RecordingGame game = new RecordingGame(players());
        GameController controller = new GameController(game, "Alice", sessions);
        String oldSessionId = sessions.registerConnection(new DummyConnection());
        sessions.authenticate(oldSessionId, "Alice");
        sessions.recordGameDisconnection(oldSessionId, game.getId());
        sessions.unregister(oldSessionId);
        DummyConnection newConnection = new DummyConnection();
        String newSessionId = sessions.registerConnection(newConnection);
        sessions.authenticate(newSessionId, "Alice");

        Thread worker = start(controller);
        controller.enqueueMessage(new SystemJoinCommand(newSessionId));

        await(() -> game.reconnectedPlayers.size() == 1);
        stop(worker);

        assertEquals("Alice", game.reconnectedPlayers.getFirst().getNickname());
        assertTrue(game.addedListeners.contains(newConnection));
        assertEquals(game.getId(), sessions.gameIdOf(newSessionId));
    }

    /**
     * A preLobby-side close command must close the model and then run the same
     * cleanup used for a normally finished match: mark the controller as
     * closing, remove routing from the registry, and clear reconnect references.
     */
    @Test
    void closeCommandFinishesTheGameAndCleansRegistryRouting() throws Exception {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        RecordingGame game = new RecordingGame(players());
        GameController controller = new GameController(game, "Alice", sessions);
        authenticatedGameSession(sessions, "Alice", game.getId(), new DummyConnection());
        sessions.registerGameOrchestrator(game.getId(), controller);
        String bobSessionId = sessions.registerConnection(new DummyConnection());
        sessions.authenticate(bobSessionId, "Bob");
        sessions.recordGameDisconnection(bobSessionId, game.getId());
        sessions.unregister(bobSessionId);

        Thread worker = start(controller);
        controller.enqueueMessage(new SystemCloseGameCommand());

        await(() -> controller.isClosing());
        worker.join(1000);

        assertTrue(game.closed);
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertTrue(controller.isClosing());
        assertEquals(null, sessions.getGameOrchestrator(game.getId()));
        assertNull(sessions.reconnectGameIdOf("Bob"));
    }

    /**
     * Creates the minimal two-player list used by all tests.
     */
    private static List<Player> players() {
        return List.of(player("Alice", TotemColor.RED), player("Bob", TotemColor.BLUE));
    }

    /**
     * Creates a player with empty resources. The controller tests care only
     * about nickname identity and the fact that the player belongs to the game.
     */
    private static Player player(String nickname, TotemColor color) {
        return new Player(nickname, 0, 0, color);
    }

    /**
     * Registers a connection, authenticates it with a nickname, and puts it in
     * the target game. This mirrors the precondition expected by
     * {@link GameController} when it resolves a client command.
     */
    private static String authenticatedGameSession(ServerSessionRegistry sessions,
                                                   String nickname,
                                                   int gameId,
                                                   DummyConnection connection) {
        String sessionId = sessions.registerConnection(connection);
        sessions.authenticate(sessionId, nickname);
        sessions.enterGame(sessionId, gameId);
        return sessionId;
    }

    /**
     * Starts the controller loop on a worker thread because production code
     * consumes commands asynchronously from the internal queue.
     */
    private static Thread start(GameController controller) {
        Thread worker = new Thread(controller);
        worker.start();
        return worker;
    }

    /**
     * Stops the controller thread after the test has observed the expected
     * effect. The join prevents a still-running worker from leaking into the
     * next test.
     */
    private static void stop(Thread worker) throws InterruptedException {
        worker.interrupt();
        worker.join(1000);
    }

    /**
     * Waits briefly for an asynchronous queue effect. This keeps the tests from
     * using fixed sleeps, while still failing quickly if the expected effect
     * never happens.
     */
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean());
    }

    private static TimerTask lastPlayerTimerTask(GameController controller) {
        try {
            Field field = GameController.class.getDeclaredField("lastPlayerTimerTask");
            field.setAccessible(true);
            return (TimerTask) field.get(controller);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot inspect last-player timer task", e);
        }
    }

    /**
     * Small observable game double used by the controller tests.
     *
     * <p>It extends {@link Game} only because {@link GameController} depends on
     * the concrete model type. The overridden methods record the public calls
     * that the controller makes, so the tests can verify orchestration without
     * relying on the full board and card rules.
     */
    private static class RecordingGame extends Game {
        private final List<String> actions = new ArrayList<>();
        private final List<Player> disconnectedPlayers = new ArrayList<>();
        private final List<Player> reconnectedPlayers = new ArrayList<>();
        private final List<Listener> addedListeners = new ArrayList<>();
        private final List<Listener> removedListeners = new ArrayList<>();
        private boolean setupCalled;
        private boolean closed;

        /**
         * Builds a minimal valid game. Empty decks are enough because the
         * overridden methods prevent these tests from entering real game setup
         * or card-drafting rules.
         */
        RecordingGame(List<Player> players) {
            super(1, players, new Board(
                    TribeDeck.fromOrderedCards(new ArrayList<Card>()),
                    BuildingDeck.fromOrderedCards(new ArrayList<Card>()),
                    players
            ));
        }

        /**
         * Records a totem command with the resolved player nickname and tile.
         */
        @Override
        public void moveTotem(Player player, String tileCode) {
            this.actions.add("moveTotem:" + player.getNickname() + ":" + tileCode);
        }

        @Override
        public void setup() {
            this.setupCalled = true;
        }

        /**
         * Records a standard card-draw command with the resolved player and card id.
         */
        @Override
        public void chooseCard(Player player, int cardId) {
            this.actions.add("chooseCard:" + player.getNickname() + ":" + cardId);
        }

        /**
         * Records an extra-draw command with the resolved player and card id.
         */
        @Override
        public void extraDrawn(Player player, int cardId) {
            this.actions.add("extraDrawn:" + player.getNickname() + ":" + cardId);
        }

        /**
         * Records real domain disconnections. Stale disconnects should not reach
         * this method.
         */
        @Override
        public void disconnectedPlayer(Player player) {
            this.disconnectedPlayers.add(player);
            this.getConnectedPlayers().put(player, false);
        }

        @Override
        public void reconnectedPlayer(Player player) {
            this.reconnectedPlayers.add(player);
            this.getConnectedPlayers().put(player, true);
        }

        @Override
        public void addListener(Listener listener) {
            this.addedListeners.add(listener);
        }

        /**
         * Records listener detachment caused by disconnect handling.
         */
        @Override
        public void removeListener(Listener listener) {
            this.removedListeners.add(listener);
        }

        /**
         * Sends a simple sentinel payload so the test can verify that the
         * controller reported an invalid game payload to the listener.
         */
        @Override
        public void reportPhaseError(Listener target) {
            target.notifyUpdate(new ErrorPayload("PHASE_ERROR", "phase-error"));
        }

        /**
         * Simulates model closure by moving the game to FINISHED. This allows
         * the controller cleanup path to run exactly as it would after a real
         * finished game.
         */
        @Override
        public void closeGame() {
            this.closed = true;
            this.setStatus(GameStatus.FINISHED);
        }
    }

    /**
     * Minimal connection/listener used by the registry. It stores delivered
     * payloads so tests can assert what the controller sent to the client.
     */
    private static class DummyConnection implements ClientConnection, Listener {
        private final List<Object> receivedPayloads = new ArrayList<>();

        /**
         * Listener callback used by the controller/game to deliver updates.
         */
        @Override
        public void notifyUpdate(ServerPayload payload) {
            this.receivedPayloads.add(payload);
        }

        /**
         * Ping is irrelevant for these controller tests.
         */
        @Override
        public void sendPing() {
        }

        /**
         * Closing the physical connection is irrelevant for these tests.
         */
        @Override
        public void close() throws IOException {
        }
    }

    /**
     * Payload that belongs to the game-payload hierarchy but is not supported by
     * {@link GameController}. It drives the invalid-payload branch.
     */
    private static class UnknownGamePayload implements GameClientPayload {
    }
}
