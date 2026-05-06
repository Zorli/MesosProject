package it.polimi.ingsoft.mesos.client.serverproxy;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiConnectionService;
import it.polimi.ingsoft.mesos.shared.RmiServerService;
import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ChooseCardPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.CreateLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExitLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExtraDrawPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.JoinLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ListGlobalRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ListLobbiesPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ListMatchHistoryPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.MoveTotemPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.PongPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.StartLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.FinalMatchRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GlobalRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.MatchHistoryPayload;
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
        proxy.listGlobalRanking();
        proxy.listMatchHistory();
        proxy.listMatchHistory(4);
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
                "listGlobalRanking",
                "listMatchHistory:null",
                "listMatchHistory:4",
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
                case AuthenticationPayload authentication -> this.record("authenticate:" + authentication.nickname);
                case ListLobbiesPayload ignored -> this.record("listLobbies");
                case CreateLobbyPayload create -> this.record("createLobby:" + create.chosenCapacity);
                case JoinLobbyPayload join -> this.record("joinLobby:" + join.lobbyId);
                case ExitLobbyPayload ignored -> this.record("exitLobby");
                case StartLobbyPayload ignored -> this.record("startLobby");
                case MoveTotemPayload move -> this.record("moveTotem:" + move.tile);
                case ChooseCardPayload choose -> this.record("chooseCard:" + choose.cardId);
                case ExtraDrawPayload extra -> this.record("extraDraw:" + extra.cardId);
                case ListGlobalRankingPayload ignored -> this.record("listGlobalRanking");
                case ListMatchHistoryPayload history -> this.record("listMatchHistory:" + history.playerCount);
                default -> this.record(payload.getClass().getSimpleName());
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

        @Override
        public void showGlobalRanking(GlobalRankingPayload payload) {
        }

        @Override
        public void showFinalMatchRanking(FinalMatchRankingPayload payload) {
        }

        @Override
        public void showMatchHistory(MatchHistoryPayload payload) {
        }
    }
}
