package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.user.User;
import it.polimi.ingsoft.mesos.shared.RmiClientService;
import it.polimi.ingsoft.mesos.shared.payload.client.StartLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import org.junit.jupiter.api.Test;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the RMI virtual-view adapter used as a match observer.
 */
class VirtualViewRMITest {

    @Test
    void startLobbyRegistersRmiSessionsOnTheSharedMatch() throws RemoteException {
        ServerController controller = new ServerController();
        RecordingClientCallback aliceCallback = new RecordingClientCallback();
        RecordingClientCallback bobCallback = new RecordingClientCallback();
        VirtualViewRMI aliceView = new VirtualViewRMI(controller, aliceCallback);
        VirtualViewRMI bobView = new VirtualViewRMI(controller, bobCallback);
        User alice = controller.authenticate("Alice");
        User bob = controller.authenticate("Bob");
        Lobby lobby = controller.createLobby(alice, 2);
        controller.joinLobby(lobby.getId(), bob);

        aliceView.getConnectionContext().authenticate(alice);
        aliceView.getConnectionContext().setCurrentLobbyId(lobby.getId());
        bobView.getConnectionContext().authenticate(bob);
        bobView.getConnectionContext().setCurrentLobbyId(lobby.getId());

        aliceView.startLobby(new StartLobbyPayload());
        bobView.startLobby(new StartLobbyPayload());

        GameOrchestrator orchestrator = controller.enterMatchFromLobby(lobby.getId());
        orchestrator.setupMatch();

        assertEquals(1, aliceCallback.gameUpdates.size());
        assertEquals(1, bobCallback.gameUpdates.size());
        assertEquals(orchestrator.getCurrentPayload().id, aliceCallback.gameUpdates.getFirst().id);
        assertEquals(orchestrator.getCurrentPayload().id, bobCallback.gameUpdates.getFirst().id);
    }

    @Test
    void closeSessionDetachesRmiObserverFromMatch() throws RemoteException {
        ServerController controller = new ServerController();
        RecordingClientCallback callback = new RecordingClientCallback();
        VirtualViewRMI view = new VirtualViewRMI(controller, callback);
        User alice = controller.authenticate("Alice");
        User bob = controller.authenticate("Bob");
        Lobby lobby = controller.createLobby(alice, 2);
        controller.joinLobby(lobby.getId(), bob);

        view.getConnectionContext().authenticate(alice);
        view.getConnectionContext().setCurrentLobbyId(lobby.getId());
        view.startLobby(new StartLobbyPayload());
        view.closeSession();

        GameOrchestrator orchestrator = controller.enterMatchFromLobby(lobby.getId());
        orchestrator.setupMatch();

        assertEquals(0, callback.gameUpdates.size());
    }

    @Test
    void failedRmiDeliveryRemovesTheObserverAfterTheFirstFailure() throws RemoteException {
        ServerController controller = new ServerController();
        FailingClientCallback callback = new FailingClientCallback();
        VirtualViewRMI view = new VirtualViewRMI(controller, callback);
        User alice = controller.authenticate("Alice");
        User bob = controller.authenticate("Bob");
        Lobby lobby = controller.createLobby(alice, 2);
        controller.joinLobby(lobby.getId(), bob);
        view.getConnectionContext().authenticate(alice);
        view.getConnectionContext().setCurrentLobbyId(lobby.getId());
        view.startLobby(new StartLobbyPayload());
        GameOrchestrator orchestrator = controller.enterMatchFromLobby(lobby.getId());

        orchestrator.setupMatch();
        orchestrator.moveTotem(orchestrator.getGame().getTurnOrderTrack().getCurrentPlayer(), "B");

        assertEquals(1, callback.gameUpdateCalls);
    }

    /**
     * Callback stub used to capture outbound RMI notifications.
     */
    private static final class RecordingClientCallback implements RmiClientService {
        private final List<GamePayload> gameUpdates = new ArrayList<>();

        @Override
        public void handleError(ErrorPayload payload) {
        }

        @Override
        public void handlePreMatchUpdate(PreMatchUpdatePayload payload) {
        }

        @Override
        public void handleLobbyList(LobbyListPayload payload) {
        }

        @Override
        public void handleGameUpdate(GamePayload payload) {
            this.gameUpdates.add(payload);
        }
    }

    /**
     * Callback stub that simulates a disconnected RMI client during game updates.
     */
    private static final class FailingClientCallback implements RmiClientService {
        private int gameUpdateCalls;

        @Override
        public void handleError(ErrorPayload payload) {
        }

        @Override
        public void handlePreMatchUpdate(PreMatchUpdatePayload payload) {
        }

        @Override
        public void handleLobbyList(LobbyListPayload payload) {
        }

        @Override
        public void handleGameUpdate(GamePayload payload) throws RemoteException {
            this.gameUpdateCalls++;
            throw new RemoteException("callback disconnected");
        }
    }
}
