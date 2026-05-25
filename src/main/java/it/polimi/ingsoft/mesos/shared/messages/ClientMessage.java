package it.polimi.ingsoft.mesos.shared.messages;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a wrapper container for any application-level messages
 * sent from the client to the server.
 * <p>
 * Each message specifies a {@link ClientMessageType} indicating how the server
 * should interpret and route the payload object.
 */
public class ClientMessage implements Serializable {
    /** The type classification of the client message. */
    public final ClientMessageType type;

    /** The actual payload containing data specific to the message type. */
    public final Object payload;

    /**
     * Constructs a new ClientMessage with the specified type and payload.
     *
     * @param type the classification of the message, cannot be null
     * @param payload the data payload carried by the message
     */
    public ClientMessage(ClientMessageType type, Object payload) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.payload = payload;
    }
}
