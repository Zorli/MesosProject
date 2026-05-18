package it.polimi.ingsoft.mesos.server.controller.command;

/**
 * Internal queue command used to start match setup after a lobby has been
 * converted into a game.
 *
 * <p>This command is not tied to a specific client action. It is scheduled by
 * the lobby controller and consumed by the game controller so that setup runs on
 * the same worker thread as every other game state transition.
 *
 * <p>The current command interface requires a {@code sessionId}. Setup has no
 * acting session, so it returns {@code null}. Controllers must handle this
 * command before resolving session-scoped data.
 */
public class SystemSetupMatchCommand implements QueuedCommand {
    @Override
    public String getSessionId() {
        return null;
    }
}
