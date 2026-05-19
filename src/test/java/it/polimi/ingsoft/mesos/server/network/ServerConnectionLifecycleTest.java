package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.command.ClientPayloadCommand;
import it.polimi.ingsoft.mesos.server.controller.game.GameController;
import it.polimi.ingsoft.mesos.server.controller.command.SystemDisconnectCommand;
import it.polimi.ingsoft.mesos.server.controller.command.SystemJoinCommand;
import it.polimi.ingsoft.mesos.server.model.game.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.server.model.server.ServerModel;
import it.polimi.ingsoft.mesos.server.virtualview.Listener;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.MoveTotemPayload;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConnectionLifecycleTest {

    @Test
    void gameDisconnectFreesNicknameBeforeGameQueueRuns() {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        ServerConnectionLifecycle lifecycle = new ServerConnectionLifecycle(sessions, new ServerModel(sessions));
        String oldSessionId = lifecycle.registerConnection(new DummyConnection());
        sessions.authenticate(oldSessionId, "Alice");
        sessions.enterGame(oldSessionId, 1);
        sessions.registerGameOrchestrator(1, gameController(sessions));

        lifecycle.disconnectSession(oldSessionId);

        assertTrue(sessions.isDisconnected("Alice"));
        String newSessionId = lifecycle.registerConnection(new DummyConnection());
        assertDoesNotThrow(() -> sessions.authenticate(newSessionId, "Alice"));
    }

    @Test
    void failedDuplicateReconnectDoesNotAuthenticateSecondSession() {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        ServerModel serverModel = new ServerModel(sessions);
        String firstSessionId = sessions.registerConnection(new DummyConnection());
        String secondSessionId = sessions.registerConnection(new DummyConnection());

        assertTrue(serverModel.authenticate(firstSessionId, "Alice", sessions.getListenerOf(firstSessionId)));
        assertFalse(serverModel.authenticate(secondSessionId, "Alice", sessions.getListenerOf(secondSessionId)));
        assertFalse(sessions.isAuthenticated(secondSessionId));
    }

    @Test
    void staleDisconnectDoesNotDisconnectPendingReconnectSession() throws InterruptedException {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        Game game = game();
        GameController controller = new GameController(game, "Alice", sessions);
        DummyConnection oldConnection = new DummyConnection();
        String oldSessionId = sessions.registerConnection(oldConnection);
        sessions.authenticate(oldSessionId, "Alice");
        sessions.enterGame(oldSessionId, game.getId());
        game.addListener(oldConnection);
        sessions.registerDisconnection("Alice", game.getId());
        sessions.unregister(oldSessionId);

        String newSessionId = sessions.registerConnection(new DummyConnection());
        sessions.authenticate(newSessionId, "Alice");

        Thread worker = new Thread(controller);
        worker.start();
        controller.enqueueMessage(new SystemDisconnectCommand(oldSessionId, "Alice", oldConnection));
        Thread.sleep(100);
        worker.interrupt();
        worker.join(1000);

        Player alice = game.getPlayers().stream()
                .filter(player -> player.getNickname().equals("Alice"))
                .findFirst()
                .orElseThrow();
        assertTrue(game.getConnectedPlayers().get(alice));
        game.notifyListeners("update");
        assertTrue(oldConnection.receivedPayloads.isEmpty());
    }

    @Test
    void queuedPayloadFromUnregisteredSessionDoesNotStopGameWorker() throws InterruptedException {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        GameController controller = gameController(sessions);
        String oldSessionId = sessions.registerConnection(new DummyConnection());
        sessions.authenticate(oldSessionId, "Alice");
        sessions.enterGame(oldSessionId, 1);
        controller.enqueueMessage(new ClientPayloadCommand(oldSessionId, new MoveTotemPayload("A1")));
        sessions.unregister(oldSessionId);

        Thread worker = new Thread(controller);
        worker.start();
        Thread.sleep(100);

        assertTrue(worker.isAlive());
        worker.interrupt();
        worker.join(1000);
    }

    @Test
    void reconnectKeepsLastPlayerTimerWhenOnlyOnePlayerIsConnected() throws Exception {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        Game game = game();
        GameController controller = new GameController(game, "Alice", sessions);
        DummyConnection aliceOldConnection = new DummyConnection();
        DummyConnection bobConnection = new DummyConnection();
        String aliceOldSessionId = sessions.registerConnection(aliceOldConnection);
        String bobSessionId = sessions.registerConnection(bobConnection);
        sessions.authenticate(aliceOldSessionId, "Alice");
        sessions.authenticate(bobSessionId, "Bob");
        sessions.enterGame(aliceOldSessionId, game.getId());
        sessions.enterGame(bobSessionId, game.getId());

        Thread worker = new Thread(controller);
        worker.start();
        controller.enqueueMessage(new SystemDisconnectCommand(bobSessionId, "Bob", bobConnection));
        Thread.sleep(100);
        assertTrue(hasLastPlayerTimer(controller));

        sessions.registerDisconnection("Alice", game.getId());
        sessions.unregister(aliceOldSessionId);
        String aliceNewSessionId = sessions.registerConnection(new DummyConnection());
        sessions.authenticate(aliceNewSessionId, "Alice");
        controller.enqueueMessage(new SystemDisconnectCommand(aliceOldSessionId, "Alice", aliceOldConnection));
        controller.enqueueMessage(new SystemJoinCommand(aliceNewSessionId));
        Thread.sleep(100);

        assertTrue(hasLastPlayerTimer(controller));
        worker.interrupt();
        worker.join(1000);
    }

    @Test
    void disconnectQueuedAfterReconnectDoesNotUndoReconnect() throws Exception {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        Game game = game();
        GameController controller = new GameController(game, "Alice", sessions);
        DummyConnection oldConnection = new DummyConnection();
        String oldSessionId = sessions.registerConnection(oldConnection);
        sessions.authenticate(oldSessionId, "Alice");
        sessions.enterGame(oldSessionId, game.getId());
        game.addListener(oldConnection);
        sessions.registerDisconnection("Alice", game.getId());
        sessions.unregister(oldSessionId);

        String newSessionId = sessions.registerConnection(new DummyConnection());
        sessions.authenticate(newSessionId, "Alice");

        Thread worker = new Thread(controller);
        worker.start();
        controller.enqueueMessage(new SystemJoinCommand(newSessionId));
        controller.enqueueMessage(new SystemDisconnectCommand(oldSessionId, "Alice", oldConnection));
        Thread.sleep(100);

        Player alice = game.getPlayers().stream()
                .filter(player -> player.getNickname().equals("Alice"))
                .findFirst()
                .orElseThrow();
        assertTrue(game.getConnectedPlayers().get(alice));
        oldConnection.receivedPayloads.clear();
        game.notifyListeners("update");
        assertTrue(oldConnection.receivedPayloads.isEmpty());
        worker.interrupt();
        worker.join(1000);
    }

    @Test
    void realLastConnectedPlayerDisconnectFinishesGameImmediately() throws Exception {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        Game game = game();
        GameController controller = new GameController(game, "Alice", sessions);
        DummyConnection aliceConnection = new DummyConnection();
        DummyConnection bobConnection = new DummyConnection();
        String aliceSessionId = sessions.registerConnection(aliceConnection);
        String bobSessionId = sessions.registerConnection(bobConnection);
        sessions.authenticate(aliceSessionId, "Alice");
        sessions.authenticate(bobSessionId, "Bob");
        sessions.enterGame(aliceSessionId, game.getId());
        sessions.enterGame(bobSessionId, game.getId());
        sessions.registerGameOrchestrator(game.getId(), controller);

        Thread worker = new Thread(controller);
        worker.start();
        controller.enqueueMessage(new SystemDisconnectCommand(bobSessionId, "Bob", bobConnection));
        Thread.sleep(100);
        controller.enqueueMessage(new SystemDisconnectCommand(aliceSessionId, "Alice", aliceConnection));
        worker.join(1000);

        assertTrue(game.getStatus() == GameStatus.FINISHED);
        assertNull(sessions.getGameOrchestrator(game.getId()));
    }

    private static GameController gameController(ServerSessionRegistry sessions) {
        return new GameController(game(), "Alice", sessions);
    }

    private static Game game() {
        Game game = DefaultGameFactory.getInstance().createGame(
                1,
                List.of(
                        new Player("Alice", 0, 0, TotemColor.RED),
                        new Player("Bob", 0, 0, TotemColor.BLUE)
                )
        );
        return game;
    }

    private static boolean hasLastPlayerTimer(GameController controller) throws ReflectiveOperationException {
        Field field = GameController.class.getDeclaredField("lastPlayerTimerTask");
        field.setAccessible(true);
        return field.get(controller) != null;
    }

    private static class DummyConnection implements ClientConnection, Listener {
        private final List<Object> receivedPayloads = new ArrayList<>();

        @Override
        public void notifyUpdate(Object payload) {
            this.receivedPayloads.add(payload);
        }

        @Override
        public void sendPing() {
        }

        @Override
        public void close() throws IOException {
        }
    }
}
