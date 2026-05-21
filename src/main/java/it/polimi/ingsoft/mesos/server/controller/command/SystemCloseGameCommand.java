package it.polimi.ingsoft.mesos.server.controller.command;

/**
 * Internal queue command used to request the termination and cleanup of a match.
 *
 * <p>This command is not triggered by a client payload. Instead, it is scheduled
 * by background lifecycle events—such as when the last remaining active player
 * in a game times out—to ensure the game teardown processes run safely and
 * sequentially on the single-threaded game controller loop.
 */
public class SystemCloseGameCommand implements QueuedCommand {
    /**
     * Returns the session identifier for this system command.
     *
     * @return {@code null} because this command is system-wide and not initiated by or
     *         bound to a specific client session
     */
    @Override
    public String getSessionId() {
        return null;
    }
}
