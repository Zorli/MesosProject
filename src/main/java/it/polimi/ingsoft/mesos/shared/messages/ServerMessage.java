package it.polimi.ingsoft.mesos.shared.messages;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a wrapper container for any application-level messages
 * sent from the server to a client.
 * <p>
 * Each message contains a {@link ServerMessageType} indicating the nature
 * of the update, and a payload object representing the specific state or result.
 */
public class ServerMessage implements Serializable {
    /** The type classification of the server message. */
    public final ServerMessageType type;

    /** The actual payload containing data specific to the message type. */
    public final Object payload;

    /**
     * Constructs a new ServerMessage with the specified type and payload.
     *
     * @param type the classification of the message, cannot be null
     * @param payload the data payload carried by the message
     */
    public ServerMessage(ServerMessageType type, Object payload) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.payload = payload;
    }
}
