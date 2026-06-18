package it.polimi.ingsoft.mesos.server.controller.command;

import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;

/**
 * Internal queue command used by the pre-lobby controller to ask a lobby controller
 * to enroll an authenticated session with the totem color selected by that player.
 *
 * <p>This command is specific to lobby joins because, unlike generic game reconnect
 * joins, entering a lobby must also reserve a totem color before the match can start.
 */
public class SystemJoinLobbyCommand extends SystemJoinCommand {
    private final TotemColor totemColor;

    /**
     * Constructs a new lobby join command.
     *
     * @param sessionId the unique identifier of the authenticated player session
     * @param totemColor the totem color requested by the joining player
     */
    public SystemJoinLobbyCommand(String sessionId, TotemColor totemColor) {
        super(sessionId);
        this.totemColor = totemColor;
    }

    /**
     * Retrieves the totem color requested by the joining player.
     *
     * @return the requested totem color
     */
    public TotemColor getTotemColor() {
        return this.totemColor;
    }
}
