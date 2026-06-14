package it.polimi.ingsoft.mesos.server.controller.command;

/**
 * Internal queue command used to route an authenticated session into a controller
 * without carrying a client payload.
 *
 * <p>The command is produced by server-side routing logic after validation has
 * selected the target controller. It carries only the {@code sessionId}; the
 * receiving controller resolves listener and nickname from {@code ServerSessionRegistry},
 * so the queue does not carry duplicated runtime state.
 *
 * <p>Lobby joins that must also reserve a totem color use
 * {@link SystemJoinLobbyCommand}. This generic command remains suitable for flows
 * where the destination already knows the player identity and no lobby-specific
 * selection has to be transported, such as game reconnect routing.
 */
public class SystemJoinCommand implements QueuedCommand {
    private final String sessionId;

    /**
     * Constructs a new system join command.
     *
     * @param sessionId the unique identifier of the authenticated player session that is joining
     */
    public SystemJoinCommand(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Retrieves the session ID of the player attempting to join.
     *
     * @return the unique session ID string
     */
    @Override
    public String getSessionId() {
        return this.sessionId;
    }
}
