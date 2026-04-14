package it.polimi.ingsoft.mesos.server.virtualview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.network.AuthenticationException;
import it.polimi.ingsoft.mesos.server.network.ClientCommandHandler;
import it.polimi.ingsoft.mesos.server.network.ConnectionContext;
import it.polimi.ingsoft.mesos.server.model.user.User;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ChooseCardPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.CreateLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExitLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExtraDrawPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.FinalScorePayload;
import it.polimi.ingsoft.mesos.shared.payload.client.JoinLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.MoveTotemPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ResolveEffectsPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ResetBoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.SetupMatchPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.StartLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
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

public class VirtualViewTCP implements VirtualView, Runnable {
    private static final Gson GSON = new GsonBuilder().create();

    private final Socket clientSocket;
    private final ServerController serverController;
    private final PrintStream out;
    private final PrintWriter writer;
    private final ConnectionContext connectionContext;
    private final ClientCommandHandler commandHandler;

    public VirtualViewTCP(Socket clientSocket, ServerController serverController) {
        this(clientSocket, serverController, System.out);
    }

    public VirtualViewTCP(Socket clientSocket, ServerController serverController, PrintStream out) {
        this.clientSocket = Objects.requireNonNull(clientSocket, "clientSocket cannot be null");
        this.serverController = Objects.requireNonNull(serverController, "serverController cannot be null");
        this.out = Objects.requireNonNull(out, "out cannot be null");
        this.connectionContext = new ConnectionContext(this.serverController);
        this.commandHandler = new ClientCommandHandler(this.out, this);
        try {
            this.writer = new PrintWriter(
                    new OutputStreamWriter(this.clientSocket.getOutputStream(), StandardCharsets.UTF_8),
                    true
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot open TCP virtual view writer", exception);
        }
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(this.clientSocket.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String rawMessage;
            while ((rawMessage = reader.readLine()) != null) {
                try {
                    ServerMessage response = this.commandHandler.handle(rawMessage, this.connectionContext);
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
        this.clientSocket.close();
    }

    //Handling the command section-------------------
    @Override
    public ServerMessage authenticate(AuthenticationPayload payload) {
        if (!this.connectionContext.isInAuthenticationPhase()) {
            throw new AuthenticationException("Client is already authenticated");
        }
        if (payload == null || payload.nickname == null || payload.nickname.isBlank()) {
            throw new IllegalArgumentException("Authentication payload must contain a nickname");
        }

        try {
            User authenticatedUser = this.serverController.authenticate(payload.nickname);
            this.connectionContext.authenticate(authenticatedUser);
        } catch (IllegalArgumentException exception) {
            throw new AuthenticationException(exception.getMessage(), exception);
        }

        PreMatchUpdatePayload updatePayload = new PreMatchUpdatePayload(
                PreMatchEventType.AUTHENTICATED,
                this.connectionContext.getNickname(),
                "Authentication completed successfully",
                null,
                null,
                null,
                null
        );
        return new ServerMessage(ServerMessageType.PRE_MATCH_UPDATE, updatePayload);
    }

    @Override
    public ServerMessage createLobby(CreateLobbyPayload payload) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ServerMessage joinLobby(JoinLobbyPayload payload) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ServerMessage exitLobby(ExitLobbyPayload payload) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ServerMessage startLobby(StartLobbyPayload payload) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ServerMessage setupMatch(SetupMatchPayload payload) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ServerMessage moveTotem(MoveTotemPayload payload) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ServerMessage chooseCard(ChooseCardPayload payload) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ServerMessage extraDraw(ExtraDrawPayload payload) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ServerMessage resolveEffects(ResolveEffectsPayload payload) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ServerMessage resetBoard(ResetBoardPayload payload) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ServerMessage finalScore(FinalScorePayload payload) {
        throw new UnsupportedOperationException("Not implemented yet");
    }


    // I/O methods section---------------
    public void sendServerMessage(ServerMessage message) {
        Objects.requireNonNull(message, "message cannot be null");
        this.writer.println(GSON.toJson(message));
    }

    // Error handling methods section
    public void sendError(Exception exception) {
        Objects.requireNonNull(exception, "exception cannot be null");
        ErrorPayload errorPayload = this.mapExceptionToErrorPayload(exception);
        this.sendServerMessage(new ServerMessage(ServerMessageType.ERROR, errorPayload));
    }

    private ErrorPayload mapExceptionToErrorPayload(Exception exception) {
        if (exception instanceof AuthenticationException) {
            return new ErrorPayload("AUTHENTICATION_FAILED", exception.getMessage());
        }
        if (exception instanceof IllegalArgumentException) {
            return new ErrorPayload("INVALID_MESSAGE", exception.getMessage());
        }
        if (exception instanceof UnsupportedOperationException) {
            return new ErrorPayload("INVALID_MESSAGE", exception.getMessage());
        }
        return new ErrorPayload("INTERNAL_ERROR", "Unexpected server error");
    }
}
