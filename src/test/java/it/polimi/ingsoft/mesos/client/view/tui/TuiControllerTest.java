package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.client.virtualserver.ServerProxy;
import it.polimi.ingsoft.mesos.shared.payload.server.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PlayerPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchEventType;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TribePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TurnOrderPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TurnStatePayload;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiControllerTest {
    @Test
    void startWithoutServerProxyFailsFast() {
        TuiController controller = new TuiController(
                new Scanner(new ByteArrayInputStream(new byte[0])),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                false
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, controller::start);

        assertEquals("serverProxy must be attached before starting the TUI", exception.getMessage());
    }

    @Test
    void startDispatchesCommandsToServerProxy() {
        FakeServerProxy serverProxy = new FakeServerProxy();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TuiController controller = new TuiController(
                new Scanner(new ByteArrayInputStream("auth Alice\nlist-lobbies\nquit\n".getBytes(StandardCharsets.UTF_8))),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                false
        );
        controller.attachServerProxy(serverProxy);

        controller.start();

        assertEquals(List.of("auth:Alice", "list-lobbies", "close"), serverProxy.calls);
        String rendered = output.toString(StandardCharsets.UTF_8);
        assertTrue(rendered.contains("Mesos TUI started."));
        assertTrue(rendered.contains("Authentication request sent."));
        assertTrue(rendered.contains("Lobby list request sent."));
    }

    @Test
    void showGameUpdateRedrawsTheDashboard() {
        FakeServerProxy serverProxy = new FakeServerProxy();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TuiController controller = new TuiController(
                new Scanner(new ByteArrayInputStream(new byte[0])),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                false
        );
        controller.attachServerProxy(serverProxy);
        controller.showPreMatchUpdate(new PreMatchUpdatePayload(
                PreMatchEventType.AUTHENTICATED,
                "Alice",
                "Authentication completed successfully",
                null,
                null,
                null,
                null
        ));

        controller.showGameUpdate(new GamePayload(
                5,
                "IN_PROGRESS",
                0,
                new TurnOrderPayload(1, "PLACING_TOTEMS", "Alice", List.of("Alice")),
                new TurnStatePayload("WAITING_FOR_CARD_SELECTION"),
                List.of(new PlayerPayload("Alice", 2, 0, "RED", new TribePayload(List.of(), List.of()))),
                new BoardPayload(List.of(), List.of(), List.of(), List.of())
        ));

        String rendered = output.toString(StandardCharsets.UTF_8);
        assertTrue(rendered.contains("Game update received."));
        assertTrue(rendered.contains("Game #5"));
        assertTrue(rendered.contains("move-totem <tile>"));
    }

    private static final class FakeServerProxy implements ServerProxy {
        private final List<String> calls = new java.util.ArrayList<>();

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void authenticate(String nickname) {
            this.calls.add("auth:" + nickname);
        }

        @Override
        public void listLobbies() {
            this.calls.add("list-lobbies");
        }

        @Override
        public void createLobby(int chosenCapacity) {
            this.calls.add("create:" + chosenCapacity);
        }

        @Override
        public void joinLobby(int lobbyId) {
            this.calls.add("join:" + lobbyId);
        }

        @Override
        public void exitLobby() {
            this.calls.add("exit-lobby");
        }

        @Override
        public void startLobby() {
            this.calls.add("start-lobby");
        }

        @Override
        public void moveTotem(String tile) {
            this.calls.add("move:" + tile);
        }

        @Override
        public void chooseCard(int cardId) {
            this.calls.add("choose:" + cardId);
        }

        @Override
        public void extraDraw(int cardId) {
            this.calls.add("extra:" + cardId);
        }

        @Override
        public void close() throws IOException {
            this.calls.add("close");
        }
    }
}
