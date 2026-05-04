package it.polimi.ingsoft.mesos.client.virtualserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxyTCP;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.payload.server.ApplicationErrorCode;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

// TCP implementation of the server -> client channel.
// It reads server messages from the socket and forwards them to the UI through ClientView.
public class VirtualServerTCP implements VirtualServer, Runnable {
    private static final Gson GSON = new GsonBuilder().create();

    private final Socket socket;
    private final ClientView clientView;
    private final ServerProxyTCP serverProxy;
    private boolean closeRequestedByClient;

    public VirtualServerTCP(Socket socket, ClientView clientView, ServerProxyTCP serverProxy) {
        this.socket = Objects.requireNonNull(socket, "socket cannot be null");
        this.clientView = Objects.requireNonNull(clientView, "clientView cannot be null");
        this.serverProxy = Objects.requireNonNull(serverProxy, "serverProxy cannot be null");
    }

    public synchronized void requestCloseByClient() {
        this.closeRequestedByClient = true;
    }

    private synchronized boolean isCloseRequestedByClient() {
        return this.closeRequestedByClient;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), StandardCharsets.UTF_8))) {
            String serverMessage;

            while ((serverMessage = reader.readLine()) != null) {
                this.handleServerMessage(serverMessage);
            }

            if (!this.isCloseRequestedByClient()) {
                this.handleError(new ErrorPayload(ApplicationErrorCode.CONNECTION_CLOSED.name(), "Connection closed by server"));
            }
        } catch (IOException exception) {
            if (!this.isCloseRequestedByClient()) {
                this.handleError(new ErrorPayload(ApplicationErrorCode.CONNECTION_CLOSED.name(), exception.getMessage()));
            }
        } finally {
            try {
                this.serverProxy.closeConnection();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void handlePing() {
        this.serverProxy.sendPong();
    }

    @Override
    public void handleError(ErrorPayload payload) {
        this.clientView.showError(payload);
    }

    @Override
    public void handlePreMatchUpdate(PreMatchUpdatePayload payload) {
        this.clientView.showPreMatchUpdate(payload);
    }

    @Override
    public void handleLobbyList(LobbyListPayload payload) {
        this.clientView.showLobbyList(payload);
    }

    @Override
    public void handleGameUpdate(GamePayload payload) {
        this.clientView.showGameUpdate(payload);
    }

    @Override
    public void close() throws IOException {
        this.serverProxy.closeConnection();
    }

    private void handleServerMessage(String serializedMessage) {
        try {
            JsonObject root = GSON.fromJson(serializedMessage, JsonObject.class);
            ServerMessageType type = ServerMessageType.valueOf(root.get("type").getAsString());
            JsonElement payload = root.get("payload");

            switch (type) {
                case ERROR -> this.handleError(payload);
                case PRE_MATCH_UPDATE -> this.handlePreMatchUpdate(payload);
                case LOBBY_LIST -> this.handleLobbyList(payload);
                case GAME_UPDATE -> this.handleGameUpdate(payload);
                case PING -> this.handlePing();
            }
        } catch (RuntimeException exception) {
            this.handleError(new ErrorPayload("INVALID_SERVER_MESSAGE", serializedMessage));
        }
    }

    private void handleError(JsonElement payload) {
        ErrorPayload errorPayload = GSON.fromJson(payload, ErrorPayload.class);
        this.handleError(errorPayload);
    }

    private void handlePreMatchUpdate(JsonElement payload) {
        PreMatchUpdatePayload updatePayload = GSON.fromJson(payload, PreMatchUpdatePayload.class);
        this.handlePreMatchUpdate(updatePayload);
    }

    private void handleLobbyList(JsonElement payload) {
        LobbyListPayload lobbyListPayload = GSON.fromJson(payload, LobbyListPayload.class);
        this.handleLobbyList(lobbyListPayload);
    }

    private void handleGameUpdate(JsonElement payload) {
        GamePayload gamePayload = GSON.fromJson(payload, GamePayload.class);
        this.handleGameUpdate(gamePayload);
    }
}
