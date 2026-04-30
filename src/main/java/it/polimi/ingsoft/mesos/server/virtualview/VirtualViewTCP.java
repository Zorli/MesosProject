package it.polimi.ingsoft.mesos.server.virtualview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.network.AuthenticationException;
import it.polimi.ingsoft.mesos.server.network.ClientCommandHandler;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.user.User;
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
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbySummaryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchEventType;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.PrintStream;
import java.net.Socket;
import java.util.List;
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
            throw new IllegalStateException("TCP virtual view failed", exception);
        } finally {
            try {
                this.close();
            } catch (IOException ignored) {
                // No-op in bootstrap skeleton.
            }
        }
    }

    @Override
    public void close() throws IOException {
        Integer lobbyId = this.safeLobbyId();
        if (lobbyId != null) {
            this.serverController.getLobbyManager().removeLobbyListener(lobbyId, this);
        }
        GameOrchestrator game = this.safeGame();
        if (game != null) {
            game.getGame().removeListener(this);
        }
        this.serverController.disconnectSession(this.sessionId);
        this.clientSocket.close();
    }

    //Handling the command section------------------- this method are called as callback by the commandhandler
    //Note that this are only stub for now. QUi fabio deve implementare i check necessari e le relative eccezioni
    //Molto importante se user non ha fatto auth non puo fare nulla
    @Override
    public ServerMessage authenticate(AuthenticationPayload payload) {
        if (this.serverController.isAuthenticated(this.sessionId)) {
            throw new AuthenticationException("Client is already authenticated");
        }
        if (payload == null || payload.nickname == null || payload.nickname.isBlank()) {
            throw new IllegalArgumentException("Authentication payload must contain a nickname");
        }

        try {
            this.serverController.authenticate(this.sessionId, payload.nickname);
        } catch (IllegalArgumentException exception) {
            throw new AuthenticationException(exception.getMessage(), exception);
        }

        PreMatchUpdatePayload updatePayload = new PreMatchUpdatePayload(
                PreMatchEventType.AUTHENTICATED,
                this.serverController.nicknameOf(this.sessionId),
                "Authentication completed successfully",
                null,
                null,
                null,
                null
        );
        return new ServerMessage(ServerMessageType.PRE_MATCH_UPDATE, updatePayload);
    }

    @Override
    public ServerMessage listLobbies(ListLobbiesPayload payload) {
        List<LobbySummaryPayload> lobbies = this.serverController.listOpenLobbies().stream()
                .map(this::toLobbySummaryPayload)
                .toList();
        return new ServerMessage(ServerMessageType.LOBBY_LIST, new LobbyListPayload(lobbies));
    }

    @Override
    public ServerMessage createLobby(CreateLobbyPayload payload) {
        Lobby lobby = this.serverController.createLobby(this.sessionId, payload.chosenCapacity);
        this.serverController.getLobbyManager().addLobbyListener(lobby.getId(), this);
        return this.buildPreMatchUpdate(
                PreMatchEventType.LOBBY_CREATED,
                this.serverController.nicknameOf(this.sessionId),
                "Lobby created successfully",
                lobby
        );
    }

    @Override
    public ServerMessage joinLobby(JoinLobbyPayload payload) {
        this.serverController.getLobbyManager().addLobbyListener(payload.lobbyId, this);
        this.serverController.joinLobby(this.sessionId, payload.lobbyId);

        return null;
    }

    @Override
    public ServerMessage exitLobby(ExitLobbyPayload payload) {
        Integer lobbyId = this.serverController.lobbyIdOf(this.sessionId);
        if (lobbyId == null) {
            throw new IllegalStateException("Client is not in a lobby");
        }
        this.serverController.exitLobby(this.sessionId);
        this.serverController.getLobbyManager().removeLobbyListener(lobbyId, this);

        return null;
    }

    @Override
    public ServerMessage startLobby(StartLobbyPayload payload) {
        this.serverController.startLobby(this.sessionId);

        return null;
    }

    @Override
    public ServerMessage moveTotem(MoveTotemPayload payload) {
        String nickname = this.serverController.nicknameOf(this.sessionId);
        this.serverController.gameOf(this.sessionId).moveTotem(nickname, payload.tile);
        return null;
    }

    @Override
    public ServerMessage chooseCard(ChooseCardPayload payload) {
        String nickname = this.serverController.nicknameOf(this.sessionId);
        this.serverController.gameOf(this.sessionId).chooseCard(nickname, payload.cardId);
        return null;
    }

    @Override
    public ServerMessage extraDraw(ExtraDrawPayload payload) {
        String nickname = this.serverController.nicknameOf(this.sessionId);
        this.serverController.gameOf(this.sessionId).extraDrawn(nickname, payload.cardId);
        return null;
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
        ErrorPayload errorPayload = this.mapExceptionToErrorPayload(exception);
        this.sendServerMessage(new ServerMessage(ServerMessageType.ERROR, errorPayload));
    }

    private ErrorPayload mapExceptionToErrorPayload(Exception exception) {
        if (exception instanceof AuthenticationException) {
            return new ErrorPayload("AUTH_ERROR", exception.getMessage());
        }
        if (exception instanceof IllegalArgumentException) {
            return new ErrorPayload("INVALID_MESSAGE", exception.getMessage());
        }
        if (exception instanceof UnsupportedOperationException) {
            return new ErrorPayload("INVALID_MESSAGE", exception.getMessage());
        }
        return new ErrorPayload("INTERNAL_ERROR", "Unexpected server error");
    }

    private ServerMessage buildPreMatchUpdate(PreMatchEventType event, String nickname, String message, Lobby lobby) {
        return this.buildPreMatchUpdate(event, nickname, message, lobby, null);
    }

    private ServerMessage buildPreMatchUpdate(
            PreMatchEventType event,
            String nickname,
            String message,
            Lobby lobby,
            Integer fallbackLobbyId
    ) {
        PreMatchUpdatePayload updatePayload = new PreMatchUpdatePayload(
                event,
                nickname,
                message,
                lobby != null ? lobby.getId() : fallbackLobbyId,
                lobby != null ? this.extractPlayers(lobby) : List.of(),
                lobby != null ? lobby.getHostNickname() : null,
                lobby != null ? lobby.getChosenCapacity() : null
        );
        return new ServerMessage(ServerMessageType.PRE_MATCH_UPDATE, updatePayload);
    }

    private LobbySummaryPayload toLobbySummaryPayload(Lobby lobby) {
        return new LobbySummaryPayload(
                lobby.getId(),
                lobby.getHostNickname(),
                lobby.getUsers().size(),
                lobby.getChosenCapacity(),
                lobby.getStatus().name()
        );
    }

    private List<String> extractPlayers(Lobby lobby) {
        return lobby.getUsers().stream()
                .map(User::getNickname)
                .toList();
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

    private Integer safeLobbyId() {
        try {
            return this.serverController.lobbyIdOf(this.sessionId);
        } catch (RuntimeException ignored) {
            return null;
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
