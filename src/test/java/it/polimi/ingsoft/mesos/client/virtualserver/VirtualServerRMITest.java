package it.polimi.ingsoft.mesos.client.serverproxy;

import it.polimi.ingsoft.mesos.client.virtualserver.VirtualServerRMI;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiConnectionService;
import it.polimi.ingsoft.mesos.shared.RmiServerService;
import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ChooseCardPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.CreateLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExitLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExtraDrawPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.JoinLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ListLobbiesPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.MoveTotemPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.PongPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.StartLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchEventType;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import org.junit.jupiter.api.Test;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VirtualServerRMITest {
    @Test
    void callbacksForwardPayloadsToClientView() throws Exception {
        RecordingClientView clientView = new RecordingClientView();
        VirtualServerRMI virtualServer = new VirtualServerRMI(clientView, false);
        ErrorPayload errorPayload = new ErrorPayload("CODE", "message");
        PreMatchUpdatePayload preMatchPayload = new PreMatchUpdatePayload(
                PreMatchEventType.AUTHENTICATED,
                "Alice",
                "ok",
                null,
                List.of(),
                null,
                null
        );
        LobbyListPayload lobbyListPayload = new LobbyListPayload(List.of());
        GamePayload gamePayload = new GamePayload(1, "RUNNING", 1, null, null, List.of(), null);

        virtualServer.handleError(errorPayload);
        virtualServer.handlePreMatchUpdate(preMatchPayload);
        virtualServer.handleLobbyList(lobbyListPayload);
        virtualServer.handleGameUpdate(gamePayload);
        virtualServer.close();

        assertSame(errorPayload, clientView.lastError);
        assertSame(preMatchPayload, clientView.lastPreMatchUpdate);
        assertSame(lobbyListPayload, clientView.lastLobbyList);
        assertSame(gamePayload, clientView.lastGameUpdate);
    }

    @Test
    void pingWithoutAttachedProxyFailsClearly() throws Exception {
        VirtualServerRMI virtualServer = new VirtualServerRMI(new RecordingClientView(), false);

        IllegalStateException exception = assertThrows(IllegalStateException.class, virtualServer::handlePing);
        virtualServer.close();

        assertEquals("Cannot reply to ping before connection", exception.getMessage());
    }

    @Test
    void pingRepliesThroughServerProxy() throws Exception {
        RecordingRmiServerService serverStub = new RecordingRmiServerService();
        ServerProxyRMI serverProxy = new ServerProxyRMI(new RecordingConnectionService(serverStub), new RecordingClientView(), false);
        VirtualServerRMI virtualServer = new VirtualServerRMI(new RecordingClientView(), false);
        virtualServer.attachServerProxy(serverProxy);

        virtualServer.handlePing();

        assertEquals(List.of("pong"), serverStub.calls);

        virtualServer.close();
        serverProxy.close();
    }

    private static final class RecordingConnectionService implements RmiConnectionService {
        private final RmiServerService serverStub;

        private RecordingConnectionService(RmiServerService serverStub) {
            this.serverStub = serverStub;
        }

        @Override
        public RmiServerService connect(RmiClientService clientCallback) {
            return this.serverStub;
        }
    }

    private static final class RecordingRmiServerService implements RmiServerService {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void authenticate(AuthenticationPayload payload) {
        }

        @Override
        public void listLobbies(ListLobbiesPayload payload) {
        }

        @Override
        public void createLobby(CreateLobbyPayload payload) {
        }

        @Override
        public void joinLobby(JoinLobbyPayload payload) {
        }

        @Override
        public void exitLobby(ExitLobbyPayload payload) {
        }

        @Override
        public void startLobby(StartLobbyPayload payload) {
        }

        @Override
        public void moveTotem(MoveTotemPayload payload) {
        }

        @Override
        public void chooseCard(ChooseCardPayload payload) {
        }

        @Override
        public void extraDraw(ExtraDrawPayload payload) {
        }

        @Override
        public void pong(PongPayload payload) {
            this.calls.add("pong");
        }

        @Override
        public void closeSession() {
        }
    }

    private static final class RecordingClientView implements ClientView {
        private ErrorPayload lastError;
        private PreMatchUpdatePayload lastPreMatchUpdate;
        private LobbyListPayload lastLobbyList;
        private GamePayload lastGameUpdate;

        @Override
        public void showError(ErrorPayload payload) {
            this.lastError = payload;
        }

        @Override
        public void showPreMatchUpdate(PreMatchUpdatePayload payload) {
            this.lastPreMatchUpdate = payload;
        }

        @Override
        public void showLobbyList(LobbyListPayload payload) {
            this.lastLobbyList = payload;
        }

        @Override
        public void showGameUpdate(GamePayload payload) {
            this.lastGameUpdate = payload;
        }
    }
}
