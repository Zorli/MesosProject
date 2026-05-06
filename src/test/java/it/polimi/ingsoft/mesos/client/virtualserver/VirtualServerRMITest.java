package it.polimi.ingsoft.mesos.client.serverproxy;

import it.polimi.ingsoft.mesos.client.virtualserver.VirtualServerRMI;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiConnectionService;
import it.polimi.ingsoft.mesos.shared.RmiServerService;
import it.polimi.ingsoft.mesos.shared.payload.client.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.PongPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.FinalMatchRankingDeltaPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.FinalMatchRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.FinalMatchRankingStatus;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GlobalRankingEntryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GlobalRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.MatchHistoryPayload;
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
        GlobalRankingPayload globalRankingPayload = new GlobalRankingPayload(List.of(
                new GlobalRankingEntryPayload("Alice", 1200, 1, 4, 2)
        ));
        FinalMatchRankingPayload finalMatchRankingPayload = new FinalMatchRankingPayload(
                "game-1",
                FinalMatchRankingStatus.RECORDED,
                List.of(new FinalMatchRankingDeltaPayload("Alice", 1000, 1020, 3, 1)),
                null
        );
        MatchHistoryPayload matchHistoryPayload = new MatchHistoryPayload(List.of());

        virtualServer.handleError(errorPayload);
        virtualServer.handlePreMatchUpdate(preMatchPayload);
        virtualServer.handleLobbyList(lobbyListPayload);
        virtualServer.handleGameUpdate(gamePayload);
        virtualServer.handleGlobalRanking(globalRankingPayload);
        virtualServer.handleFinalMatchRanking(finalMatchRankingPayload);
        virtualServer.handleMatchHistory(matchHistoryPayload);
        virtualServer.close();

        assertSame(errorPayload, clientView.lastError);
        assertSame(preMatchPayload, clientView.lastPreMatchUpdate);
        assertSame(lobbyListPayload, clientView.lastLobbyList);
        assertSame(gamePayload, clientView.lastGameUpdate);
        assertSame(globalRankingPayload, clientView.lastGlobalRanking);
        assertSame(finalMatchRankingPayload, clientView.lastFinalMatchRanking);
        assertSame(matchHistoryPayload, clientView.lastMatchHistory);
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
        public void sendMessage(ClientPayload payload) {
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
        private GlobalRankingPayload lastGlobalRanking;
        private FinalMatchRankingPayload lastFinalMatchRanking;
        private MatchHistoryPayload lastMatchHistory;

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

        @Override
        public void showGlobalRanking(GlobalRankingPayload payload) {
            this.lastGlobalRanking = payload;
        }

        @Override
        public void showFinalMatchRanking(FinalMatchRankingPayload payload) {
            this.lastFinalMatchRanking = payload;
        }

        @Override
        public void showMatchHistory(MatchHistoryPayload payload) {
            this.lastMatchHistory = payload;
        }
    }
}
