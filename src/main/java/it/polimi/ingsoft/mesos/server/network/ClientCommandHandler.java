package it.polimi.ingsoft.mesos.server.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessageType;
import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.server.virtualview.VirtualView;

import java.io.PrintStream;
import java.util.Objects;


// !!!!! Vital !!!!!
// Command handlers call application-layer methods that may throw exceptions.
// When a called method can fail with a generic or domain-level exception, catch it here
// and rethrow a more precise protocol-facing exception. The VirtualView boundary then
// catches that exception and maps it to the correct ERROR message for the client.
//!!!!!!!!!

//Here we maintain only the de-serialization logic and the parsing logic, all the business logic is inside the virtualviewtcp
//that implements the common contract in virtualview. So here in the call we check the payload and the TCP semantics
//In the virtualview method we check for the applicability of that method(everythin non related to network)
public class ClientCommandHandler {
    private static final Gson GSON = new GsonBuilder().create();

    private final PrintStream out;
    private final VirtualView virtualView;

    public ClientCommandHandler(PrintStream out, VirtualView virtualView) {
        this.out = Objects.requireNonNull(out, "out cannot be null");
        this.virtualView = Objects.requireNonNull(virtualView, "virtualView cannot be null");
    }

    // Entry point
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

        // Command dispatch
        return switch (type) {
            case AUTHENTICATE -> this.handleAuthentication(payload, context);
            default -> throw new UnsupportedOperationException("Unsupported client message type: " + type);
        };
    }

    // Command-specific payload handling
    private ServerMessage handleAuthentication(JsonElement payload, ConnectionContext context) {
        AuthenticationPayload authenticationPayload;
        try {
            authenticationPayload = GSON.fromJson(payload, AuthenticationPayload.class);
            if (authenticationPayload == null) {
                throw new IllegalArgumentException("Malformed authentication payload");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed authentication payload", exception);
        }

        return this.virtualView.authenticate(authenticationPayload);
    }

    // Helper methods section
    private String describeConnection(ConnectionContext context) {
        if (context.getNickname() != null && !context.getNickname().isBlank()) {
            return context.getNickname();
        }
        return "unauthenticated-client";
    }
}
