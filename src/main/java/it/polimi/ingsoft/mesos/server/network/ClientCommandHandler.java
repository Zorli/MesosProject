package it.polimi.ingsoft.mesos.server.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.server.model.user.User;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessageType;
import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchEventType;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.io.PrintStream;
import java.util.Objects;


// !!!!! Vital !!!!!
// Command handlers call application-layer methods that may throw exceptions.
// When a called method can fail with a generic or domain-level exception, catch it here
// and rethrow a more precise protocol-facing exception. The VirtualView boundary then
// catches that exception and maps it to the correct ERROR message for the client.
//!!!!!!!!!
public class ClientCommandHandler {
    private static final Gson GSON = new GsonBuilder().create();

    private final PrintStream out;

    public ClientCommandHandler(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out cannot be null");
    }

    public ServerMessage handle(String rawMessage, ConnectionContext context) {

        JsonObject root = GSON.fromJson(rawMessage, JsonObject.class);
        if (root == null) {
            throw new IllegalArgumentException("Malformed client message");
        }

        JsonElement typeElement = root.get("type");
        if (typeElement == null || typeElement.isJsonNull()) {
            throw new IllegalArgumentException("Missing client message type");
        }

        ClientMessageType type;
        try {
            type = ClientMessageType.valueOf(typeElement.getAsString());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unknown client message type", exception);
        }

        JsonElement payload = root.get("payload");
        this.out.println("[TCP] Received " + type
                + " from " + this.describeConnection(context)
                + " payload=" + (payload == null ? "null" : payload));

        return switch (type) {
            case AUTHENTICATE -> this.handleAuthentication(payload, context);
            default -> throw new UnsupportedOperationException("Unsupported client message type: " + type);
        };
    }

    private ServerMessage handleAuthentication(JsonElement payload, ConnectionContext context) {
        if (!context.isInAuthenticationPhase()) {
            throw new AuthenticationException("Client is already authenticated");
        }

        AuthenticationPayload authenticationPayload;
        try {
            authenticationPayload = GSON.fromJson(payload, AuthenticationPayload.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed authentication payload", exception);
        }

        if (authenticationPayload == null
                || authenticationPayload.nickname == null
                || authenticationPayload.nickname.isBlank()) {
            throw new IllegalArgumentException("Authentication payload must contain a nickname");
        }

        try {
            User authenticatedUser = context.getServerController().authenticate(authenticationPayload.nickname);
            context.authenticate(authenticatedUser);
        } catch (IllegalArgumentException exception) {
            throw new AuthenticationException(exception.getMessage(), exception);
        }

        PreMatchUpdatePayload updatePayload = new PreMatchUpdatePayload(
                PreMatchEventType.AUTHENTICATED,
                context.getNickname(),
                "Authentication completed successfully",
                null,
                null,
                null,
                null
        );

        return new ServerMessage(ServerMessageType.PRE_MATCH_UPDATE, updatePayload);
    }

    private String describeConnection(ConnectionContext context) {
        if (context.getNickname() != null && !context.getNickname().isBlank()) {
            return context.getNickname();
        }
        return "unauthenticated-client";
    }
}
