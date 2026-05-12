package it.polimi.ingsoft.mesos.client.view;

import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyEventType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageRouterTest {

    @Test
    void routesErrorPayloadToClientView() {
        RecordingClientView view = new RecordingClientView();
        MessageRouter router = new MessageRouter(view);
        ErrorPayload payload = new ErrorPayload("CODE", "message");

        router.routeMessage(new ServerMessage(ServerMessageType.ERROR, payload));

        assertEquals("showError", view.lastCall);
        assertSame(payload, view.errorPayload);
    }

    @Test
    void routesLobbyUpdatePayloadToClientView() {
        RecordingClientView view = new RecordingClientView();
        MessageRouter router = new MessageRouter(view);
        LobbyUpdatePayload payload = new LobbyUpdatePayload(
                LobbyEventType.LOBBY_JOINED,
                "ada",
                "joined",
                1,
                List.of("ada"),
                "ada",
                2
        );

        router.routeMessage(new ServerMessage(ServerMessageType.LOBBY_UPDATE, payload));

        assertEquals("showLobbyUpdate", view.lastCall);
        assertSame(payload, view.lobbyUpdatePayload);
    }

    @Test
    void routesLobbyListPayloadToClientView() {
        RecordingClientView view = new RecordingClientView();
        MessageRouter router = new MessageRouter(view);
        LobbyListPayload payload = new LobbyListPayload(List.of());

        router.routeMessage(new ServerMessage(ServerMessageType.LOBBY_LIST, payload));

        assertEquals("showLobbyList", view.lastCall);
        assertSame(payload, view.lobbyListPayload);
    }

    @Test
    void routesRunningGameUpdateToClientView() {
        RecordingClientView view = new RecordingClientView();
        MessageRouter router = new MessageRouter(view);
        GamePayload payload = gamePayload("IN_PROGRESS");

        router.routeMessage(new ServerMessage(ServerMessageType.GAME_UPDATE, payload));

        assertEquals("showGameUpdate", view.lastCall);
        assertSame(payload, view.gamePayload);
    }

    @Test
    void routesFinishedGameUpdateToFinalResult() {
        RecordingClientView view = new RecordingClientView();
        MessageRouter router = new MessageRouter(view);
        GamePayload payload = gamePayload("FINISHED");

        router.routeMessage(new ServerMessage(ServerMessageType.GAME_UPDATE, payload));

        assertEquals("showFinalGameResult", view.lastCall);
        assertSame(payload, view.finalGamePayload);
    }

    @Test
    void rejectsPingMessages() {
        RecordingClientView view = new RecordingClientView();
        MessageRouter router = new MessageRouter(view);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> router.routeMessage(new ServerMessage(ServerMessageType.PING, null))
        );

        assertEquals("PING must be handled by the virtual server", exception.getMessage());
    }

    @Test
    void requiresClientView() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new MessageRouter(null)
        );

        assertEquals("clientView cannot be null", exception.getMessage());
    }

    @Test
    void requiresMessage() {
        RecordingClientView view = new RecordingClientView();
        MessageRouter router = new MessageRouter(view);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> router.routeMessage(null)
        );

        assertEquals("message cannot be null", exception.getMessage());
    }

    private static GamePayload gamePayload(String status) {
        return new GamePayload(1, status, 0, null, null, List.of(), null);
    }

    private static final class RecordingClientView implements ClientView {
        private String lastCall;
        private ErrorPayload errorPayload;
        private LobbyUpdatePayload lobbyUpdatePayload;
        private LobbyListPayload lobbyListPayload;
        private GamePayload gamePayload;
        private GamePayload finalGamePayload;

        @Override
        public void showError(ErrorPayload payload) {
            this.lastCall = "showError";
            this.errorPayload = payload;
        }

        @Override
        public void showLobbyUpdate(LobbyUpdatePayload payload) {
            this.lastCall = "showLobbyUpdate";
            this.lobbyUpdatePayload = payload;
        }

        @Override
        public void showLobbyList(LobbyListPayload payload) {
            this.lastCall = "showLobbyList";
            this.lobbyListPayload = payload;
        }

        @Override
        public void showGameUpdate(GamePayload payload) {
            this.lastCall = "showGameUpdate";
            this.gamePayload = payload;
        }

        @Override
        public void showFinalGameResult(GamePayload payload) {
            this.lastCall = "showFinalGameResult";
            this.finalGamePayload = payload;
        }
    }
}
