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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class ClientHandler implements Runnable {
    private static final Gson GSON = new GsonBuilder().create();

    private final Socket socket;
    private final ServerMessageListener listener;

    public ClientHandler(Socket socket, ServerMessageListener listener) {
        this.socket = Objects.requireNonNull(socket, "socket cannot be null");
        this.listener = Objects.requireNonNull(listener, "listener cannot be null");
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
            this.listener.onServerMessage("[SERVER] connection closed: " + exception.getMessage());
        }
    }

    private void handleServerMessage(String serializedMessage) {
        try {
            JsonObject root = GSON.fromJson(serializedMessage, JsonObject.class);
            ServerMessageType type = ServerMessageType.valueOf(root.get("type").getAsString());
            JsonElement payload = root.get("payload");

            switch (type) {
                case ERROR -> this.handleError(payload);
                case GAME_UPDATE -> this.listener.onServerMessage("[SERVER] " + serializedMessage);
            }
        } catch (RuntimeException exception) {
            this.listener.onServerMessage("[SERVER] " + serializedMessage);
        }
    }

    private void handleError(JsonElement payload) {
        ErrorPayload errorPayload = GSON.fromJson(payload, ErrorPayload.class);
        this.listener.onServerMessage("[SERVER][ERROR] " + errorPayload.code + ": " + errorPayload.message);
    }
}
