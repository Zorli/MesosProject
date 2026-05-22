package it.polimi.ingsoft.mesos.server.controller.command;

/**
 * Internal queue command used to move an authenticated session into a lobby.
 *
 * <p>The command is produced by server-side routing logic after validation has
 * selected the target controller. It carries only the {@code sessionId}. The
 * receiving controller resolves listener and nickname from
 * {@code ServerSessionRegistry}, so the queue does not carry duplicated runtime
 * state.
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
