package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxyRMI;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiConnectionService;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiServerService;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.connection.PongPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbySummaryPayload;
import org.junit.jupiter.api.Test;

import java.rmi.RemoteException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VirtualServerRMITest {

    @Test
    void errorMessageIsRoutedToClientView() throws Exception {
        RecordingClientView clientView = new RecordingClientView();
        VirtualServerRMI virtualServer = new VirtualServerRMI(clientView, false);

        ErrorPayload payload = new ErrorPayload("SERVER_ERROR", "no");
        ServerMessage message = new ServerMessage(ServerMessageType.ERROR, payload);

        virtualServer.handleMessage(message);

        assertEquals("SERVER_ERROR", clientView.lastError.code);
        assertEquals("no", clientView.lastError.message);
    }

    @Test
    void lobbyListMessageIsRoutedToClientView() throws Exception {
        RecordingClientView clientView = new RecordingClientView();
        VirtualServerRMI virtualServer = new VirtualServerRMI(clientView, false);

        LobbySummaryPayload lobby = new LobbySummaryPayload(7, "ada", 1, 4, "WAITING");
        LobbyListPayload payload = new LobbyListPayload(List.of(lobby));
        ServerMessage message = new ServerMessage(ServerMessageType.LOBBY_LIST, payload);

        virtualServer.handleMessage(message);

        assertEquals(1, clientView.lastLobbyList.lobbies.size());
        assertEquals(7, clientView.lastLobbyList.lobbies.get(0).lobbyId);
        assertEquals("ada", clientView.lastLobbyList.lobbies.get(0).hostNickname);
        assertNull(clientView.lastError);
    }

    @Test
    void pingSendsPongWithoutRoutingToView() throws Exception {
        try (ServerProxyHarness harness = new ServerProxyHarness()) {
            RecordingClientView clientView = new RecordingClientView();
            VirtualServerRMI virtualServer = new VirtualServerRMI(clientView, false);
            virtualServer.attachServerProxy(harness.proxy);

            ServerMessage message = new ServerMessage(ServerMessageType.PING, null);

            virtualServer.handleMessage(message);

            assertNotNull(harness.server.lastPong);
            assertNull(clientView.lastError);
            assertNull(clientView.lastLobbyList);
            assertNull(clientView.lastLobbyUpdate);
            assertNull(clientView.lastGameUpdate);
            assertNull(clientView.lastFinalGameResult);
        }
    }

    @Test
    void pingWithoutAttachedProxyThrowsError() throws Exception {
        RecordingClientView clientView = new RecordingClientView();
        VirtualServerRMI virtualServer = new VirtualServerRMI(clientView, false);

        ServerMessage message = new ServerMessage(ServerMessageType.PING, null);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> virtualServer.handleMessage(message)
        );

        assertEquals("Cannot reply to ping before connection", exception.getMessage());
    }

    private static final class ServerProxyHarness implements AutoCloseable {
        private final RecordingServerService server = new RecordingServerService();
        private final ServerProxyRMI proxy;

        private ServerProxyHarness() throws RemoteException {
            this.proxy = new ServerProxyRMI(
                    new StubConnectionService(this.server),
                    new RecordingClientView()
            );
        }

        @Override
        public void close() throws Exception {
            this.proxy.close();
        }
    }

    private static final class StubConnectionService implements RmiConnectionService {
        private final RecordingServerService server;

        private StubConnectionService(RecordingServerService server) {
            this.server = server;
        }

        @Override
        public RmiServerService connect(RmiClientService clientCallback) {
            return this.server;
        }
    }

    private static final class RecordingServerService implements RmiServerService {
        private PongPayload lastPong;

        @Override
        public void sendMessage(ClientPayload payload) {
        }

        @Override
        public void pong(PongPayload payload) {
            this.lastPong = payload;
        }

        @Override
        public void closeSession() {
        }
    }

    private static final class RecordingClientView implements ClientView {
        private ErrorPayload lastError;
        private LobbyUpdatePayload lastLobbyUpdate;
        private LobbyListPayload lastLobbyList;
        private GamePayload lastGameUpdate;
        private GamePayload lastFinalGameResult;

        @Override
        public void showError(ErrorPayload payload) {
            this.lastError = payload;
        }

        @Override
        public void showLobbyUpdate(LobbyUpdatePayload payload) {
            this.lastLobbyUpdate = payload;
        }

        @Override
        public void showLobbyList(LobbyListPayload payload) {
            this.lastLobbyList = payload;
        }

        @Override
        public void showGameUpdate(GamePayload payload) {
            this.lastGameUpdate = payload;
        }

        @Override
        public void showFinalGameResult(GamePayload payload) {
            this.lastFinalGameResult = payload;
        }
    }
}
