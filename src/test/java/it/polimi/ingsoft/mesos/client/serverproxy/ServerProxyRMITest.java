package it.polimi.ingsoft.mesos.client.serverproxy;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiConnectionService;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiServerService;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.connection.PongPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.CreateLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.JoinLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.ListLobbiesPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.AuthenticationResultPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import org.junit.jupiter.api.Test;

import java.rmi.RemoteException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerProxyRMITest {

    @Test
    void authenticateSendsAuthenticationPayload() throws Exception {
        RmiHarness harness = new RmiHarness();

        harness.proxy.authenticate("ada");

        AuthenticationPayload payload = assertInstanceOf(AuthenticationPayload.class, harness.server.lastPayload);
        assertEquals("ada", payload.nickname);
    }

    @Test
    void createLobbySendsCreateLobbyPayload() throws Exception {
        RmiHarness harness = new RmiHarness();

        harness.proxy.createLobby(4);

        CreateLobbyPayload payload = assertInstanceOf(CreateLobbyPayload.class, harness.server.lastPayload);
        assertEquals(4, payload.chosenCapacity);
    }

    @Test
    void listLobbiesSendsListLobbiesPayload() throws Exception {
        RmiHarness harness = new RmiHarness();

        harness.proxy.listLobbies();

        assertInstanceOf(ListLobbiesPayload.class, harness.server.lastPayload);
    }

    @Test
    void joinLobbySendsJoinLobbyPayload() throws Exception {
        RmiHarness harness = new RmiHarness();

        harness.proxy.joinLobby(7);

        JoinLobbyPayload payload = assertInstanceOf(JoinLobbyPayload.class, harness.server.lastPayload);
        assertEquals(7, payload.lobbyId);
    }

    @Test
    void remoteExceptionReportsConnectionClosed() throws Exception {
        RmiHarness harness = new RmiHarness();
        harness.server.sendFailure = new RemoteException("rmi failed");

        harness.proxy.authenticate("ada");

        assertFalse(harness.proxy.isConnected());
        assertEquals("CONNECTION_CLOSED", harness.clientView.lastError.code);
        assertEquals("rmi failed", harness.clientView.lastError.message);
    }

    @Test
    void closeClosesRemoteSession() throws Exception {
        RmiHarness harness = new RmiHarness();

        harness.proxy.close();

        assertFalse(harness.proxy.isConnected());
        assertTrue(harness.server.closeSessionCalled);
    }

    private static final class RmiHarness {
        private final RecordingServerService server = new RecordingServerService();
        private final RecordingClientView clientView = new RecordingClientView();
        private final ServerProxyRMI proxy;

        private RmiHarness() throws RemoteException {
            this.proxy = new ServerProxyRMI(new StubConnectionService(this.server), this.clientView, false);
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
        private ClientPayload lastPayload;
        private PongPayload lastPong;
        private boolean closeSessionCalled;
        private RemoteException sendFailure;

        @Override
        public void sendMessage(ClientPayload payload) throws RemoteException {
            if (this.sendFailure != null) {
                throw this.sendFailure;
            }

            this.lastPayload = payload;
        }

        @Override
        public void pong(PongPayload payload) {
            this.lastPong = payload;
        }

        @Override
        public void closeSession() {
            this.closeSessionCalled = true;
        }
    }

    private static final class RecordingClientView implements ClientView {
        private ErrorPayload lastError;

        @Override
        public void showError(ErrorPayload payload) {
            this.lastError = payload;
        }

        @Override
        public void showAuthenticationResult(AuthenticationResultPayload payload) {
        }

        @Override
        public void showLobbyUpdate(LobbyUpdatePayload payload) {
        }

        @Override
        public void showLobbyList(LobbyListPayload payload) {
        }

        @Override
        public void showGameUpdate(GamePayload payload) {
        }
    }
}
