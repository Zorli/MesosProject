package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.game.GameController;
import it.polimi.ingsoft.mesos.server.controller.command.SystemDisconnectCommand;
import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.server.model.server.ServerModel;
import it.polimi.ingsoft.mesos.server.virtualview.Listener;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        String oldSessionId = sessions.registerConnection(new DummyConnection());
        sessions.authenticate(oldSessionId, "Alice");
        sessions.enterGame(oldSessionId, game.getId());
        sessions.registerDisconnection("Alice", game.getId());
        sessions.unregister(oldSessionId);

        String newSessionId = sessions.registerConnection(new DummyConnection());
        sessions.authenticate(newSessionId, "Alice");

        Thread worker = new Thread(controller);
        worker.start();
        controller.enqueueMessage(new SystemDisconnectCommand(oldSessionId, "Alice", new DummyConnection()));
        Thread.sleep(100);
        worker.interrupt();
        worker.join(1000);

        Player alice = game.getPlayers().stream()
                .filter(player -> player.getNickname().equals("Alice"))
                .findFirst()
                .orElseThrow();
        assertTrue(game.getConnectedPlayers().get(alice));
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
