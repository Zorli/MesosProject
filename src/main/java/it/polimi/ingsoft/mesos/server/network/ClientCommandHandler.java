package it.polimi.ingsoft.mesos.server.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessageType;

import java.io.PrintStream;
import java.util.Objects;

public class ClientCommandHandler {
    private static final Gson GSON = new GsonBuilder().create();

    private final PrintStream out;

    public ClientCommandHandler(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out cannot be null");
    }

    public void handle(String rawMessage, ConnectionContext context) {
        JsonObject root = GSON.fromJson(rawMessage, JsonObject.class);
        ClientMessageType type = ClientMessageType.valueOf(root.get("type").getAsString());
        JsonElement payload = root.get("payload");

        this.out.println("[TCP] Received " + type
                + " -> " + this.resolveTarget(type, context)
                + " payload=" + (payload == null ? "null" : payload));
    }

    private String resolveTarget(ClientMessageType type, ConnectionContext context) {
        return switch (type) {
            case AUTHENTICATE, CREATE_LOBBY, JOIN_LOBBY, EXIT_LOBBY, START_LOBBY ->
                    "ServerController";
            case SETUP_MATCH, MOVE_TOTEM, CHOOSE_CARD, EXTRA_DRAW, RESOLVE_EFFECTS, RESET_BOARD, FINAL_SCORE ->
                    context.isInMatch() ? "GameOrchestrator" : "GameOrchestrator(not set)";
        };
    }
}
