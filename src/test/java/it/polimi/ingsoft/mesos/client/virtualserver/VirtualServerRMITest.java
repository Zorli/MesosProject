package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbySummaryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchEventType;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import org.junit.jupiter.api.Test;

import java.rmi.server.UnicastRemoteObject;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class VirtualServerRMITest {
    @Test
    void callbacksAreForwardedToClientView() throws Exception {
        RecordingClientView clientView = new RecordingClientView();
        VirtualServerRMI virtualServerRMI = new VirtualServerRMI(clientView);

        try {
            ErrorPayload errorPayload = new ErrorPayload("CODE", "message");
            PreMatchUpdatePayload preMatchPayload = new PreMatchUpdatePayload(
                    PreMatchEventType.AUTHENTICATED,
                    "Alice",
                    "Authentication completed successfully",
                    null,
                    null,
                    null,
                    null
            );
            LobbyListPayload lobbyListPayload = new LobbyListPayload(List.of(
                    new LobbySummaryPayload(5, "Alice", 1, 2, "OPEN")
            ));
            GamePayload gamePayload = new GamePayload(7, "IN_PROGRESS", 0, null, null, List.of(), null);

            virtualServerRMI.handleError(errorPayload);
            virtualServerRMI.handlePreMatchUpdate(preMatchPayload);
            virtualServerRMI.handleLobbyList(lobbyListPayload);
            virtualServerRMI.handleGameUpdate(gamePayload);

            assertSame(errorPayload, clientView.errorPayload);
            assertSame(preMatchPayload, clientView.preMatchPayload);
            assertSame(lobbyListPayload, clientView.lobbyListPayload);
            assertSame(gamePayload, clientView.gamePayload);
            assertEquals("Alice", clientView.preMatchPayload.nickname);
        } finally {
            UnicastRemoteObject.unexportObject(virtualServerRMI, true);
        }
    }

    private static final class RecordingClientView implements ClientView {
        private ErrorPayload errorPayload;
        private PreMatchUpdatePayload preMatchPayload;
        private LobbyListPayload lobbyListPayload;
        private GamePayload gamePayload;

        @Override
        public void showError(ErrorPayload payload) {
            this.errorPayload = payload;
        }

        @Override
        public void showPreMatchUpdate(PreMatchUpdatePayload payload) {
            this.preMatchPayload = payload;
        }

        @Override
        public void showLobbyList(LobbyListPayload payload) {
            this.lobbyListPayload = payload;
        }

        @Override
        public void showGameUpdate(GamePayload payload) {
            this.gamePayload = payload;
        }
    }
}
