package it.polimi.ingsoft.mesos.client.virtualserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxyTCP;
import it.polimi.ingsoft.mesos.client.view.MessageRouter;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.PreMatchUpdatePayload;

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
    private final ServerProxyTCP serverProxy;
    private boolean closeRequestedByClient;
    private final MessageRouter messageRouter;

    public VirtualServerTCP(Socket socket, ClientView clientView, ServerProxyTCP serverProxy) {
        this.socket = Objects.requireNonNull(socket, "socket cannot be null");
        this.messageRouter = new MessageRouter(clientView);
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
                this.handleError(new ErrorPayload("CONNECTION_CLOSED", "Connection closed by server"));
            }
        } catch (IOException exception) {
            if (!this.isCloseRequestedByClient()) {
                this.handleError(new ErrorPayload("CONNECTION_CLOSED", exception.getMessage()));
            }
        } finally {
            try {
                this.serverProxy.closeConnection();
            } catch (IOException ignored) {
            }
        }
    }

    private void handleServerMessage(String serializedMessage) {
        try {
            JsonObject root = GSON.fromJson(serializedMessage, JsonObject.class);
            ServerMessageType type = ServerMessageType.valueOf(root.get("type").getAsString());
            JsonElement payload = root.get("payload");

            ServerMessage message = null;
            switch (type) {
                case ERROR -> message = new ServerMessage(type, GSON.fromJson(payload, ErrorPayload.class));
                case PRE_MATCH_UPDATE -> message = new ServerMessage(type, GSON.fromJson(payload, PreMatchUpdatePayload.class));
                case LOBBY_LIST -> message = new ServerMessage(type, GSON.fromJson(payload, LobbyListPayload.class));
                case GAME_UPDATE -> message= new ServerMessage(type, GSON.fromJson(payload, GamePayload.class));
                case PING -> {
                    this.handlePing();
                    return;
                }
            };
            this.handleMessage(message);
        } catch (RuntimeException exception) {
            this.handleError(new ErrorPayload("INVALID_SERVER_MESSAGE", serializedMessage));
        }
    }


    @Override
    public void handleMessage(ServerMessage message) {
        this.messageRouter.routeMessage(message);
    }

    @Override
    public void handlePing() {
        this.serverProxy.sendPong();
    }


    public void handleError(ErrorPayload payload) {
        this.messageRouter.routeMessage(new ServerMessage(ServerMessageType.ERROR, payload));
    }

    @Override
    public void close() throws IOException {
        this.serverProxy.closeConnection();
    }
}
