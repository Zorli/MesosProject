package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.client.virtualserver.ServerProxy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiCommandRouterTest {
    private final TuiCommandRouter router = new TuiCommandRouter();

    @Test
    void routeHelpDelegatesToLocalActionsOnly() throws IOException {
        FakeServerProxy serverProxy = new FakeServerProxy();
        FakeLocalActions localActions = new FakeLocalActions();

        TuiCommandRouter.RouteResult result = this.router.route(
                new TuiCommand(TuiCommand.Type.HELP, null, null),
                serverProxy,
                localActions
        );

        assertTrue(localActions.helpShown);
        assertFalse(localActions.statusShown);
        assertEquals(List.of(), serverProxy.calls);
        assertFalse(result.shouldExit());
        assertFalse(result.promptOnly());
    }

    @Test
    void routeCreateLobbyDispatchesToServerProxy() throws IOException {
        FakeServerProxy serverProxy = new FakeServerProxy();

        TuiCommandRouter.RouteResult result = this.router.route(
                new TuiCommand(TuiCommand.Type.CREATE_LOBBY, null, 4),
                serverProxy,
                new FakeLocalActions()
        );

        assertEquals(List.of("create:4"), serverProxy.calls);
        assertEquals("Create-lobby request sent.", result.feedbackMessage());
        assertFalse(result.shouldExit());
    }

    @Test
    void routeQuitClosesProxyAndRequestsExit() throws IOException {
        FakeServerProxy serverProxy = new FakeServerProxy();

        TuiCommandRouter.RouteResult result = this.router.route(
                new TuiCommand(TuiCommand.Type.QUIT, null, null),
                serverProxy,
                new FakeLocalActions()
        );

        assertEquals(List.of("close"), serverProxy.calls);
        assertTrue(result.shouldExit());
        assertEquals("Client closed.", result.feedbackMessage());
    }

    private static final class FakeLocalActions implements TuiCommandRouter.LocalActions {
        private boolean helpShown;
        private boolean statusShown;

        @Override
        public void showHelp() {
            this.helpShown = true;
        }

        @Override
        public void showStatus() {
            this.statusShown = true;
        }
    }

    private static final class FakeServerProxy implements ServerProxy {
        private final List<String> calls = new ArrayList<>();

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
