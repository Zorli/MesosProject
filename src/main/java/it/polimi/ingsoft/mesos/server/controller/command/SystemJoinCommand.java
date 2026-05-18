package it.polimi.ingsoft.mesos.server.controller.command;

/**
 * Internal queue command used to move an authenticated session into a lobby or
 * back into a resumable controller flow.
 *
 * <p>The command is produced by server-side routing logic after validation has
 * selected the target controller. It carries only the {@code sessionId}. The
 * receiving controller resolves listener and nickname from
 * {@code ServerSessionRegistry}, so the queue does not carry duplicated runtime
 * state.
 */
public class SystemJoinCommand implements QueuedCommand {
    private final String sessionId;

    public SystemJoinCommand(String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public String getSessionId() {
        return this.sessionId;
    }
}
