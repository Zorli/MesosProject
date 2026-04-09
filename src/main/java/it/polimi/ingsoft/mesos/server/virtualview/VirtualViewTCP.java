package it.polimi.ingsoft.mesos.server.virtualview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.network.ClientCommandHandler;
import it.polimi.ingsoft.mesos.server.network.ConnectionContext;
import it.polimi.ingsoft.mesos.server.model.user.User;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessageType;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;

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
    private final ConnectionContext connectionContext;
    private final ClientCommandHandler commandHandler;
    private final PrintStream out;
    private final PrintWriter writer;

    public VirtualViewTCP(Socket clientSocket, ServerController serverController) {
        this(clientSocket, serverController, System.out);
    }

    public VirtualViewTCP(Socket clientSocket, ServerController serverController, PrintStream out) {
        this.clientSocket = Objects.requireNonNull(clientSocket, "clientSocket cannot be null");
        Objects.requireNonNull(serverController, "serverController cannot be null");
        this.connectionContext = new ConnectionContext(serverController);
        this.out = Objects.requireNonNull(out, "out cannot be null");
        this.commandHandler = new ClientCommandHandler(this.out);
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
            if (!this.awaitAuthentication(reader)) {
                return;
            }

            String rawMessage;
            while ((rawMessage = reader.readLine()) != null) {
                this.commandHandler.handle(rawMessage, this.connectionContext);
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

    private boolean awaitAuthentication(BufferedReader reader) throws IOException {
        while (!this.connectionContext.isAuthenticated()) {
            String rawMessage = reader.readLine();
            if (rawMessage == null) {
                return false;
            }

            this.handleAuthenticationMessage(rawMessage);
        }
        return true;
    }

    private void handleAuthenticationMessage(String rawMessage) {
        try {
            JsonObject root = GSON.fromJson(rawMessage, JsonObject.class);
            ClientMessageType type = ClientMessageType.valueOf(root.get("type").getAsString());

            if (type != ClientMessageType.AUTHENTICATE) {
                this.out.println("[TCP] Ignored pre-authentication message of type " + type);
                return;
            }

            AuthenticationPayload authenticationPayload =
                    GSON.fromJson(root.get("payload"), AuthenticationPayload.class);
            String nickname = this.extractNickname(authenticationPayload);
            User authenticatedUser = this.connectionContext.getServerController().authenticate(nickname);
            this.connectionContext.setUser(authenticatedUser);
            this.connectionContext.setNickname(nickname);
            this.out.println("[TCP] Authentication successful for nickname=" + nickname);
        } catch (RuntimeException exception) {
            this.out.println("[TCP] Authentication failed: " + exception.getMessage());
            this.sendError("AUTHENTICATION_FAILED", exception.getMessage());
        }
    }

    private String extractNickname(AuthenticationPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Authentication payload cannot be null");
        }
        if (payload.nickname == null || payload.nickname.isBlank()) {
            throw new IllegalArgumentException("Authentication payload must contain nickname");
        }
        return payload.nickname;
    }

    private void sendError(String code, String message) {
        ErrorPayload errorPayload = new ErrorPayload(code, message);
        ServerMessage serverMessage = new ServerMessage(ServerMessageType.ERROR, errorPayload);
        this.writer.println(GSON.toJson(serverMessage));
    }
}
