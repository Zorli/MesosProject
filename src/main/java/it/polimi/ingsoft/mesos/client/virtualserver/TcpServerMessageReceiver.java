package it.polimi.ingsoft.mesos.client.virtualserver;


//This class should offer the read socket service, given that is a blockign loop(and it's used only for tcp)
//we must run in on a different thread , when it receives something from the network it passes it to the contrller
//that handles it with a callback
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
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

public class TcpServerMessageReceiver implements Runnable {
    private static final Gson GSON = new GsonBuilder().create();

    private final Socket socket;
    private final VirtualServer virtualServer;

    public TcpServerMessageReceiver(Socket socket, VirtualServer virtualServer) {
        this.socket = Objects.requireNonNull(socket, "socket cannot be null");
        this.virtualServer = Objects.requireNonNull(virtualServer, "virtualServer cannot be null");
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(this.socket.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String serverMessage;
            while ((serverMessage = reader.readLine()) != null) {
                this.handleServerMessage(serverMessage);
            }
        } catch (IOException exception) {
            this.virtualServer.handleError(new ErrorPayload("CONNECTION_CLOSED", exception.getMessage()));
        }
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
                case PING -> this.virtualServer.handlePing();
            }
        } catch (RuntimeException exception) {
            this.virtualServer.handleError(new ErrorPayload("INVALID_SERVER_MESSAGE", serializedMessage));
        }
    }


    //Helper to handle here---
    private void handleError(JsonElement payload) {
        ErrorPayload errorPayload = GSON.fromJson(payload, ErrorPayload.class);
        this.virtualServer.handleError(errorPayload);
    }

    private void handlePreMatchUpdate(JsonElement payload) {
        PreMatchUpdatePayload updatePayload = GSON.fromJson(payload, PreMatchUpdatePayload.class);
        this.virtualServer.handlePreMatchUpdate(updatePayload);
    }

    private void handleLobbyList(JsonElement payload) {
        LobbyListPayload lobbyListPayload = GSON.fromJson(payload, LobbyListPayload.class);
        this.virtualServer.handleLobbyList(lobbyListPayload);
    }

    private void handleGameUpdate(JsonElement payload) {
        GamePayload gamePayload = GSON.fromJson(payload, GamePayload.class);
        this.virtualServer.handleGameUpdate(gamePayload);
    }
}
