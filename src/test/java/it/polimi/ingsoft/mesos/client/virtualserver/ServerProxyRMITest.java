package it.polimi.ingsoft.mesos.client.virtualserver;

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
import it.polimi.ingsoft.mesos.shared.payload.client.StartLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.rmi.RemoteException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerProxyRMITest {
    @Test
    void constructorRejectsNullLoginFacadeImmediately() throws Exception {
        assertConstructorRejectsNullLoginFacade(new RecordingClientView());
    }

    @Test
    void constructorRejectsNullClientViewImmediately() throws Exception {
        assertConstructorRejectsNullClientView(new RecordingRmiConnectionService(null));
    }

    @Test
    void authenticateThenListLobbiesDelegatesToRemoteStub() throws Exception {
        RecordingClientView clientView = new RecordingClientView();
        RecordingRmiServerService serverService = new RecordingRmiServerService();
        RecordingRmiConnectionService loginFacade = new RecordingRmiConnectionService(serverService);
        ServerProxyRMI serverProxyRMI = new ServerProxyRMI(loginFacade, clientView);

        try {
            serverProxyRMI.authenticate("Alice");
            serverProxyRMI.listLobbies();

            assertEquals("Alice", loginFacade.authenticationPayload.nickname);
            assertNotNull(loginFacade.clientCallback);
            assertTrue(serverService.listLobbiesCalled);
        } finally {
            serverProxyRMI.close();
        }
    }

    @Test
    void listLobbiesWithoutAuthenticationNotifiesLocalError() throws Exception {
        RecordingClientView clientView = new RecordingClientView();
        ServerProxyRMI serverProxyRMI = new ServerProxyRMI(new RecordingRmiConnectionService(null), clientView);

        try {
            serverProxyRMI.listLobbies();

            assertNotNull(clientView.errorPayload);
            assertEquals("AUTH_ERROR", clientView.errorPayload.code);
            assertEquals("Client is not authenticated", clientView.errorPayload.message);
        } finally {
            serverProxyRMI.close();
        }
    }

    /**
     * Verifies the null-login-facade contract without leaking the callback receiver
     * when current production code unexpectedly accepts the dependency.
     *
     * @param clientView valid view used to isolate the rejected dependency
     */
    private static void assertConstructorRejectsNullLoginFacade(ClientView clientView) throws Exception {
        ServerProxyRMI serverProxyRMI = null;
        try {
            serverProxyRMI = new ServerProxyRMI(null, clientView);
            fail("ServerProxyRMI should reject a null loginFacade before exporting callbacks");
        } catch (Exception exception) {
            assertInstanceOf(NullPointerException.class, exception);
            assertEquals("loginFacade cannot be null", exception.getMessage());
        } finally {
            if (serverProxyRMI != null) {
                serverProxyRMI.close();
            }
        }
    }

    /**
     * Verifies the null-client-view contract without leaking the callback receiver
     * when current production code unexpectedly accepts the dependency.
     *
     * @param loginFacade valid RMI facade used to isolate the rejected dependency
     */
    private static void assertConstructorRejectsNullClientView(RmiConnectionService loginFacade) throws Exception {
        ServerProxyRMI serverProxyRMI = null;
        try {
            serverProxyRMI = new ServerProxyRMI(loginFacade, null);
            fail("ServerProxyRMI should reject a null clientView before exporting callbacks");
        } catch (Exception exception) {
            assertInstanceOf(NullPointerException.class, exception);
            assertEquals("clientView cannot be null", exception.getMessage());
        } finally {
            if (serverProxyRMI != null) {
                serverProxyRMI.close();
            }
        }
    }

    private static final class RecordingRmiConnectionService implements RmiConnectionService {
        private final RmiServerService serverService;
        private AuthenticationPayload authenticationPayload;
        private RmiClientService clientCallback;

        private RecordingRmiConnectionService(RmiServerService serverService) {
            this.serverService = serverService;
        }

        @Override
        public RmiServerService authenticate(AuthenticationPayload payload, RmiClientService clientCallback) {
            this.authenticationPayload = payload;
            this.clientCallback = clientCallback;
            return this.serverService;
        }
    }

    private static final class RecordingRmiServerService implements RmiServerService {
        private boolean listLobbiesCalled;

        @Override
        public void listLobbies(ListLobbiesPayload payload) {
            this.listLobbiesCalled = true;
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
        public void closeSession() {
        }
    }

    private static final class RecordingClientView implements ClientView {
        private ErrorPayload errorPayload;

        @Override
        public void showError(ErrorPayload payload) {
            this.errorPayload = payload;
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
