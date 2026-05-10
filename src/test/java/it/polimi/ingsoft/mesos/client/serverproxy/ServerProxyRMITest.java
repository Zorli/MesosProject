package it.polimi.ingsoft.mesos.client.serverproxy;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiConnectionService;
import it.polimi.ingsoft.mesos.shared.RmiServerService;
import it.polimi.ingsoft.mesos.shared.payload.client.*;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import org.junit.jupiter.api.Test;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerProxyRMITest {
    @Test
    void outboundCommandsCallRmiServerStub() throws Exception {
        RecordingRmiServerService serverStub = new RecordingRmiServerService();
        RecordingClientView clientView = new RecordingClientView();
        ServerProxyRMI proxy = new ServerProxyRMI(new RecordingConnectionService(serverStub), clientView, false);

        proxy.authenticate("Alice");
        proxy.listLobbies();
        proxy.createLobby(2);
        proxy.joinLobby(7);
        proxy.exitLobby();
        proxy.startLobby();
        proxy.moveTotem("A1");
        proxy.chooseCard(11);
        proxy.extraDraw(12);
        proxy.sendPong();
        proxy.close();

        assertEquals(List.of(
                "authenticate:Alice",
                "listLobbies",
                "createLobby:2",
                "joinLobby:7",
                "exitLobby",
                "startLobby",
                "moveTotem:A1",
                "chooseCard:11",
                "extraDraw:12",
                "pong",
                "closeSession"
        ), serverStub.calls);
        assertFalse(proxy.isConnected());
    }

    @Test
    void remoteExceptionNotifiesClientViewAboutClosedConnection() throws Exception {
        RecordingRmiServerService serverStub = new RecordingRmiServerService();
        serverStub.failNextCall = true;
        RecordingClientView clientView = new RecordingClientView();
        ServerProxyRMI proxy = new ServerProxyRMI(new RecordingConnectionService(serverStub), clientView, false);

        proxy.listLobbies();

        assertFalse(proxy.isConnected());
        assertEquals("CONNECTION_CLOSED", clientView.errors.getFirst().code);
        assertTrue(clientView.errors.getFirst().message.contains("connection lost"));

        proxy.close();
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

    static final class RecordingRmiServerService implements RmiServerService {
        private final List<String> calls = new ArrayList<>();
        private boolean failNextCall;

        @Override
        public void sendMessage(ClientPayload payload) throws RemoteException {
            switch (payload) {
                case AuthenticationPayload p -> this.record("authenticate:" + p.nickname);
                case ListLobbiesPayload p -> this.record("listLobbies");
                case CreateLobbyPayload p -> this.record("createLobby:" + p.chosenCapacity);
                case JoinLobbyPayload p -> this.record("joinLobby:" + p.lobbyId);
                case ExitLobbyPayload p -> this.record("exitLobby");
                case StartLobbyPayload p -> this.record("startLobby");
                case MoveTotemPayload p -> this.record("moveTotem:" + p.tile);
                case ChooseCardPayload p -> this.record("chooseCard:" + p.cardId);
                case ExtraDrawPayload p -> this.record("extraDraw:" + p.cardId);
                default -> this.record("unknown");
            }
        }

        @Override
        public void pong(PongPayload payload) throws RemoteException {
            this.record("pong");
        }

        @Override
        public void closeSession() throws RemoteException {
            this.record("closeSession");
        }

        private void record(String call) throws RemoteException {
            if (this.failNextCall) {
                this.failNextCall = false;
                throw new RemoteException("connection lost");
            }
            this.calls.add(call);
        }
    }

    private static final class RecordingClientView implements ClientView {
        private final List<ErrorPayload> errors = new ArrayList<>();

        @Override
        public void showError(ErrorPayload payload) {
            this.errors.add(payload);
        }

        @Override
        public void showPreMatchUpdate(PreMatchUpdatePayload payload) {
        }

        @Override
        public void showLobbyList(LobbyListPayload payload) {
        }

        @Override
        public void showGameUpdate(GamePayload payload) {
        }
    }
}
