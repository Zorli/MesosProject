package it.polimi.ingsoft.mesos.server.network.tcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessage;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessageType;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.*;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.connection.*;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.*;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.lobby.*;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.*;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Converts one raw TCP line received from a client into a typed client command.
 * <p>
 * This class is part of the server-side TCP boundary, but it does not manage
 * the socket. {@link VirtualViewTCP} reads raw text lines from the connection
 * and passes each line here. This handler only understands the JSON protocol:
 * it validates the common message envelope, deserializes the payload required
 * by the declared {@link ClientMessageType}, and gives the resulting command
 * back to the virtual view as a {@link ClientMessage}.
 * <p>
 * The handler deliberately does not execute lobby or game logic. After parsing,
 * normal commands are forwarded to {@link VirtualViewTCP#handleClientMessage(ClientMessage)}.
 * The only command handled directly here is {@link ClientMessageType#PONG},
 * because it belongs to the connection keep-alive protocol and must update the
 * connection state instead of reaching the application controllers.
 * <p>
 * In the TCP protocol every client message must contain both {@code type} and
 * {@code payload}. This also applies to {@code PONG}: the payload may be empty,
 * but the envelope is still expected to contain it so that all client messages
 * keep the same shape.
 */
public class ClientCommandHandler {
    private static final Gson GSON = new GsonBuilder().create();

    private final PrintStream out;
    private final VirtualViewTCP virtualView;

    /**
     * Creates a command handler bound to one TCP virtual view.
     *
     * @param out stream used for server-side diagnostic messages
     * @param virtualView virtual view that owns the client session and receives
     *                    the parsed command
     * @throws NullPointerException if {@code out} or {@code virtualView} is null
     */
    public ClientCommandHandler(PrintStream out, VirtualViewTCP virtualView) {
        this.out = Objects.requireNonNull(out, "out cannot be null");
        this.virtualView = Objects.requireNonNull(virtualView, "virtualView cannot be null");
    }

    /**
     * Parses and dispatches one raw client message.
     * <p>
     * The input must be a JSON object with a {@code type} field matching a
     * {@link ClientMessageType} value and a non-null {@code payload} field. The
     * declared type selects the concrete {@link ClientPayload} class used for
     * deserialization.
     * <p>
     * For regular commands, the method builds a {@link ClientMessage} and passes
     * it to the associated {@link VirtualViewTCP}. For {@code PONG}, it updates
     * the keep-alive state directly through {@link VirtualViewTCP#pong}.
     *
     * @param rawMessage JSON line received from the TCP socket
     * @throws IllegalArgumentException if the JSON envelope, message type, or
     *                                  payload is malformed
     * @throws UnsupportedOperationException if the message type is valid but not
     *                                       supported by this TCP deserializer
     */
    public void handle(String rawMessage) {
        JsonObject root;
        try {
            root = GSON.fromJson(rawMessage, JsonObject.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed client message JSON", exception);
        }
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
            throw new IllegalArgumentException("Unknown client message type: " + typeElement.getAsString(), exception);
        }

        JsonElement payloadElement = root.get("payload");
        if (payloadElement == null || payloadElement.isJsonNull()) {
            throw new IllegalArgumentException("Missing payload for type " + type);
        }
        this.out.println("[TCP] Received " + type + " from " + this.virtualView.describeSession());

        if (type == ClientMessageType.PONG) {
            this.virtualView.pong(new PongPayload());
            return;
        }

        ClientPayload payload = deserializePayload(type, payloadElement);

        if (payload == null) {
            throw new IllegalArgumentException("Malformed payload for type " + type);
        }
        ClientMessage clientMessage = new ClientMessage(type, payload);
        this.virtualView.handleClientMessage(clientMessage);
    }

    /**
     * Converts the JSON payload into the concrete payload class required by the
     * already validated message type.
     * <p>
     * This method centralizes the TCP deserialization table: each
     * {@link ClientMessageType} accepted by the TCP protocol is mapped to the
     * corresponding {@link ClientPayload} implementation used by controllers and
     * model-facing server code.
     *
     * @param type message type declared in the client envelope
     * @param payloadElement JSON payload associated with the message type
     * @return payload object matching the declared client command
     * @throws IllegalArgumentException if the payload cannot be deserialized into
     *                                  the expected class
     * @throws UnsupportedOperationException if the type is valid but has no TCP
     *                                       payload mapping here
     */
    private ClientPayload deserializePayload(ClientMessageType type, JsonElement payloadElement) {
        try {
            return switch (type) {
                case AUTHENTICATE -> GSON.fromJson(payloadElement, AuthenticationPayload.class);
                case LIST_LOBBIES -> GSON.fromJson(payloadElement, ListLobbiesPayload.class);
                case CREATE_LOBBY -> GSON.fromJson(payloadElement, CreateLobbyPayload.class);
                case JOIN_LOBBY   -> GSON.fromJson(payloadElement, JoinLobbyPayload.class);
                case EXIT_LOBBY   -> GSON.fromJson(payloadElement, ExitLobbyPayload.class);
                case START_LOBBY  -> GSON.fromJson(payloadElement, StartLobbyPayload.class);
                case MOVE_TOTEM   -> GSON.fromJson(payloadElement, MoveTotemPayload.class);
                case CHOOSE_CARD  -> GSON.fromJson(payloadElement, ChooseCardPayload.class);
                case EXTRA_DRAW   -> GSON.fromJson(payloadElement, ExtraDrawPayload.class);
                case SKIP_EXTRA_DRAW -> GSON.fromJson(payloadElement, SkipExtraDrawPayload.class);
                default -> throw new UnsupportedOperationException("Unsupported client message type: " + type);
            };
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed payload for " + type, exception);
        }
    }
}
