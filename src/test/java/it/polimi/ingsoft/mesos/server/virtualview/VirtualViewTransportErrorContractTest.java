package it.polimi.ingsoft.mesos.server.virtualview;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.shared.RmiClientService;
import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExitLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.JoinLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ApplicationErrorCode;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualViewTransportErrorContractTest {
    private static final Gson GSON = new Gson();

    @Test
    void unauthenticatedCommandHasSameErrorCodeOnTcpAndRmi() throws Exception {
        String tcpCode = runTcpAndReturnLastErrorCode("""
                {"type":"JOIN_LOBBY","payload":{"lobbyId":1}}
                """);
        String rmiCode = runRmiAndReturnLastErrorCode(view -> view.joinLobby(new JoinLobbyPayload(1)));

        assertEquals(ApplicationErrorCode.AUTH_ERROR.name(), tcpCode);
        assertEquals(tcpCode, rmiCode);
    }

    @Test
    void exitLobbyWithoutLobbyHasSameErrorCodeOnTcpAndRmi() throws Exception {
        String tcpCode = runTcpAndReturnLastErrorCode("""
                {"type":"AUTHENTICATE","payload":{"nickname":"Alice"}}
                {"type":"EXIT_LOBBY","payload":{}}
                """);
        String rmiCode = runRmiAndReturnLastErrorCode(view -> {
            view.authenticate(new AuthenticationPayload("Alice"));
            view.exitLobby(new ExitLobbyPayload());
        });

        assertEquals(ApplicationErrorCode.NOT_IN_LOBBY.name(), tcpCode);
        assertEquals(tcpCode, rmiCode);
    }

    private static String runTcpAndReturnLastErrorCode(String rawMessages) {
        RecordingSocket socket = new RecordingSocket(rawMessages);
        new VirtualViewTCP(socket, new ServerController()).run();

        String[] lines = socket.output().strip().split("\\R");
        JsonObject payload = GSON.fromJson(lines[lines.length - 1], JsonObject.class)
                .getAsJsonObject("payload");
        return payload.get("code").getAsString();
    }

    private static String runRmiAndReturnLastErrorCode(RmiInvocation invocation) throws Exception {
        RecordingRmiClient callback = new RecordingRmiClient();
        VirtualViewRMI view = new VirtualViewRMI(new ServerController(), callback);
        try {
            invocation.invoke(view);
            return callback.errors.getLast().code;
        } finally {
            view.close();
        }
    }

    private static final class RecordingSocket extends Socket {
        private final ByteArrayInputStream input;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private boolean closed;

        private RecordingSocket(String rawMessages) {
            this.input = new ByteArrayInputStream(rawMessages.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getInputStream() {
            return this.input;
        }

        @Override
        public OutputStream getOutputStream() {
            return this.output;
        }

        @Override
        public synchronized void close() throws IOException {
            this.closed = true;
            super.close();
        }

        @Override
        public boolean isClosed() {
            return this.closed;
        }

        private String output() {
            return this.output.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class RecordingRmiClient implements RmiClientService {
        private final List<ErrorPayload> errors = new ArrayList<>();

        @Override
        public void handleError(ErrorPayload payload) {
            this.errors.add(payload);
        }

        @Override
        public void handlePreMatchUpdate(PreMatchUpdatePayload payload) {
        }

        @Override
        public void handleLobbyList(LobbyListPayload payload) {
        }

        @Override
        public void handleGameUpdate(GamePayload payload) {
        }

        @Override
        public void handlePing() {
        }
    }

    @FunctionalInterface
    private interface RmiInvocation {
        void invoke(VirtualViewRMI view) throws RemoteException;
    }
}
