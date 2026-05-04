package it.polimi.ingsoft.mesos.server.virtualview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.network.ClientCommandHandler;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
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
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchEventType;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

//TCP implementation of the VirtualView contract.
//It acts as the network boundary on the server side: it reads raw client messages,
//delegates parsing/dispatch, and serializes the structured server response back on the socket.
public class VirtualViewTCP implements VirtualView, Runnable, Listener{
    private static final Gson GSON = new GsonBuilder().create();

    private final Socket clientSocket;
    private final ServerController serverController;
    private final PrintStream out;
    private final PrintWriter writer;
    private final String sessionId;
    private final ClientCommandHandler commandHandler;
    private final ServerCommandApplicationService applicationService;
    private final ApplicationErrorMapper errorMapper;
    private boolean cleanupDone;

    //Consutrctor
    public VirtualViewTCP(Socket clientSocket, ServerController serverController) {
        this(clientSocket, serverController, System.out);
    }

    public VirtualViewTCP(Socket clientSocket, ServerController serverController, PrintStream out) {
        this.clientSocket = Objects.requireNonNull(clientSocket, "clientSocket cannot be null");
        this.serverController = Objects.requireNonNull(serverController, "serverController cannot be null");
        this.out = Objects.requireNonNull(out, "out cannot be null");
        try {
            this.writer = new PrintWriter(
                    new OutputStreamWriter(this.clientSocket.getOutputStream(), StandardCharsets.UTF_8),
                    true
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot open TCP virtual view writer", exception);
        }
        this.sessionId = this.serverController.registerConnection(this);
        this.commandHandler = new ClientCommandHandler(this.out, this);
        this.applicationService = new ServerCommandApplicationService(this.serverController, this.sessionId);
        this.errorMapper = new ApplicationErrorMapper();
    }

    //Main Loop on the socket
    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(this.clientSocket.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String rawMessage;
            while ((rawMessage = reader.readLine()) != null) {
                try {
                    ServerMessage response = this.commandHandler.handle(rawMessage);
                    if (response != null) {
                        this.sendServerMessage(response);
                    }
                } catch (RuntimeException exception) {
                    this.sendError(exception);
                }
            }
        } catch (IOException exception) {
            if (!this.cleanupDone) {
                this.out.println("[TCP] Connection closed for " + this.describeSession() + ": " + exception.getMessage());
            }
        } finally {
            try {
                this.close();
            } catch (IOException ignored) {
                // No-op in bootstrap skeleton.
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (this.cleanupDone) {
            return;
        }
        this.cleanupDone = true;
        GameOrchestrator game = this.safeGame();
        if (game != null) {
            game.getGame().removeListener(this);
        }
        this.serverController.disconnectSession(this.sessionId);
        if (!this.clientSocket.isClosed()) {
            this.clientSocket.close();
        }
    }

    //Handling the command section------------------- this method are called as callback by the commandhandler
    //Note that this are only stub for now. QUi fabio deve implementare i check necessari e le relative eccezioni
    //Molto importante se user non ha fatto auth non puo fare nulla
    @Override
    public ServerMessage authenticate(AuthenticationPayload payload) {
        return this.applicationService.authenticate(payload);
    }

    @Override
    public ServerMessage listLobbies(ListLobbiesPayload payload) {
        return this.applicationService.listLobbies(payload);
    }

    @Override
    public ServerMessage createLobby(CreateLobbyPayload payload) {
        return this.applicationService.createLobby(payload);
    }

    @Override
    public ServerMessage joinLobby(JoinLobbyPayload payload) {
        return this.applicationService.joinLobby(payload);
    }

    @Override
    public ServerMessage exitLobby(ExitLobbyPayload payload) {
        return this.applicationService.exitLobby(payload);
    }

    @Override
    public ServerMessage startLobby(StartLobbyPayload payload) {
        return this.applicationService.startLobby(payload);
    }

    @Override
    public ServerMessage moveTotem(MoveTotemPayload payload) {
        return this.applicationService.moveTotem(payload);
    }

    @Override
    public ServerMessage chooseCard(ChooseCardPayload payload) {
        return this.applicationService.chooseCard(payload);
    }

    @Override
    public ServerMessage extraDraw(ExtraDrawPayload payload) {
        return this.applicationService.extraDraw(payload);
    }


    // I/O methods section---------------
    public void sendServerMessage(ServerMessage message) {//low level call
        Objects.requireNonNull(message, "message cannot be null");
        this.writer.println(GSON.toJson(message));
    }

    @Override
    public void sendPing() {
        this.sendServerMessage(new ServerMessage(ServerMessageType.PING, null));
    }

    @Override
    public void recordPong() {
        this.serverController.recordPong(this.sessionId);
    }

    @Override
    public boolean isAuthenticated() {
        return this.serverController.isAuthenticated(this.sessionId);
    }

    @Override
    public String describeSession() {
        String nickname = this.serverController.nicknameOf(this.sessionId);
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        return "unauthenticated-client";
    }

    // Error handling methods section
    public void sendError(Exception exception) {
        Objects.requireNonNull(exception, "exception cannot be null");
        ErrorPayload errorPayload = this.errorMapper.toPayload(exception);
        this.sendServerMessage(new ServerMessage(ServerMessageType.ERROR, errorPayload));
    }


    @Override
    public void notifyUpdate(Object payload) {
        if (payload instanceof GamePayload gamePayload) {
            this.enterCurrentMatchIfAvailable();
            this.sendServerMessage(new ServerMessage(ServerMessageType.GAME_UPDATE, gamePayload));
            return;
        }
        if (payload instanceof ErrorPayload errorPayload) {
            this.sendServerMessage(new ServerMessage(ServerMessageType.ERROR, errorPayload));
            return;
        }
        if (payload instanceof PreMatchUpdatePayload preMatchUpdatePayload) {
            if (preMatchUpdatePayload.event == PreMatchEventType.LOBBY_STARTED){
                this.enterMatchIfAvailable(preMatchUpdatePayload.lobbyId);
            }
            this.sendServerMessage(new ServerMessage(ServerMessageType.PRE_MATCH_UPDATE, preMatchUpdatePayload));
        }
    }

    private void enterCurrentMatchIfAvailable() {
        Integer lobbyId = this.serverController.lobbyIdOf(this.sessionId);
        if (lobbyId != null) {
            this.enterMatchIfAvailable(lobbyId);
        }
    }

    private void enterMatchIfAvailable(int lobbyId) {
        try {
            this.serverController.enterGameIfAvailable(this.sessionId, lobbyId);
        } catch (Exception ignored) {
        }
    }

    private GameOrchestrator safeGame() {
        try {
            return this.serverController.gameOf(this.sessionId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
