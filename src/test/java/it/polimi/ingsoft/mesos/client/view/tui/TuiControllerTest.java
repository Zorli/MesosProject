package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.client.virtualserver.ServerProxy;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiControllerTest {
    private static final Pattern STATUS_HELP_LINE = Pattern.compile("(?m)^\\s*status(\\s.*)?$");

    @Test
    void startRequiresAttachedServerProxy() {
        TuiHarness harness = new TuiHarness("");

        IllegalStateException exception = assertThrows(IllegalStateException.class, harness.controller::start);

        assertEquals("serverProxy must be attached before starting the TUI", exception.getMessage());
    }

    @Test
    void commandsDispatchToServerProxy() {
        TuiHarness harness = new TuiHarness("""
                auth ada
                list-lobbies
                create-lobby 2
                join-lobby 7
                quit-lobby
                start-lobby
                move-totem A1
                choose-card 11
                extra-draw 12
                """);
        FakeServerProxy proxy = new FakeServerProxy();
        harness.controller.attachServerProxy(proxy);

        harness.controller.start();

        assertEquals(List.of(
                "authenticate:ada",
                "listLobbies",
                "createLobby:2",
                "joinLobby:7",
                "exitLobby",
                "startLobby",
                "moveTotem:A1",
                "chooseCard:11",
                "extraDraw:12",
                "close"
        ), proxy.calls);
        assertTrue(proxy.closed);
    }

    @Test
    void helpOutputDoesNotListStatus() {
        TuiHarness harness = new TuiHarness("""
                help
                quit
                """);
        FakeServerProxy proxy = new FakeServerProxy();
        harness.controller.attachServerProxy(proxy);

        harness.controller.start();

        String output = harness.output();
        assertTrue(output.contains("General:"));
        assertTrue(output.contains("help"));
        assertTrue(output.contains("auth <nickname>"));
        assertFalse(STATUS_HELP_LINE.matcher(output).find());
    }

    @Test
    void blankLinesReprintPrompt() {
        TuiHarness harness = new TuiHarness("""

                quit
                """);
        FakeServerProxy proxy = new FakeServerProxy();
        harness.controller.attachServerProxy(proxy);

        harness.controller.start();

        assertEquals(3, countPrompts(harness.output()));
    }

    @Test
    void invalidCommandPrintsClientError() {
        TuiHarness harness = new TuiHarness("""
                status
                quit
                """);
        FakeServerProxy proxy = new FakeServerProxy();
        harness.controller.attachServerProxy(proxy);

        harness.controller.start();

        assertTrue(harness.output().contains("[CLIENT][ERROR] Unknown command. Type 'help'."));
    }

    @Test
    void quitClosesProxyAndExits() {
        TuiHarness harness = new TuiHarness("""
                quit
                auth unreachable
                """);
        FakeServerProxy proxy = new FakeServerProxy();
        harness.controller.attachServerProxy(proxy);

        harness.controller.start();

        assertTrue(proxy.closed);
        assertEquals(List.of("close"), proxy.calls);
        assertTrue(harness.output().contains("Client closed."));
    }

    private static long countPrompts(String output) {
        return output.chars()
                .filter(character -> character == '>')
                .count();
    }

    private static final class TuiHarness {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final TuiController controller;

        private TuiHarness(String input) {
            this.controller = new TuiController(
                    new Scanner(input),
                    new PrintStream(this.bytes, true, StandardCharsets.UTF_8)
            );
        }

        private String output() {
            return this.bytes.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class FakeServerProxy implements ServerProxy {
        private final List<String> calls = new ArrayList<>();
        private boolean closed;

        @Override
        public boolean isConnected() {
            return !this.closed;
        }

        @Override
        public void authenticate(String nickname) {
            this.calls.add("authenticate:" + nickname);
        }

        @Override
        public void listLobbies() {
            this.calls.add("listLobbies");
        }

        @Override
        public void createLobby(int chosenCapacity) {
            this.calls.add("createLobby:" + chosenCapacity);
        }

        @Override
        public void joinLobby(int lobbyId) {
            this.calls.add("joinLobby:" + lobbyId);
        }

        @Override
        public void exitLobby() {
            this.calls.add("exitLobby");
        }

        @Override
        public void startLobby() {
            this.calls.add("startLobby");
        }

        @Override
        public void moveTotem(String tile) {
            this.calls.add("moveTotem:" + tile);
        }

        @Override
        public void chooseCard(int cardId) {
            this.calls.add("chooseCard:" + cardId);
        }

        @Override
        public void extraDraw(int cardId) {
            this.calls.add("extraDraw:" + cardId);
        }

        @Override
        public void close() throws IOException {
            this.closed = true;
            this.calls.add("close");
        }
    }
}
