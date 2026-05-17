package it.polimi.ingsoft.mesos.server.controller.command;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;

/**
 * Queue command created from a payload sent by a client.
 *
 * <p>This class is the only command that carries a {@link ClientPayload}. The
 * payload still represents the protocol-level request received from TCP or RMI,
 * while the command wrapper adds the {@code sessionId} needed by controllers to
 * resolve the current runtime state through {@code ServerSessionRegistry}.
 *
 * <p>Internal server events must not be wrapped here. They have their own
 * command classes, for example {@link SystemJoinCommand},
 * {@link SystemDisconnectCommand}, and {@link SystemSetupMatchCommand}. This
 * keeps the controller queue generic without making client payloads the generic
 * container for everything.
 */
public class ClientPayloadCommand implements QueuedCommand {
    private final String sessionId;
    private final ClientPayload payload;

    public ClientPayloadCommand(String sessionId, ClientPayload payload) {
        this.sessionId = sessionId;
        this.payload = payload;
    }

    @Override
    public String getSessionId() {
        return this.sessionId;
    }

    public ClientPayload getPayload() {
        return this.payload;
    }
}
