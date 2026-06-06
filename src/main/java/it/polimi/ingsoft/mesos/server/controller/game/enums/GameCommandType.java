package it.polimi.ingsoft.mesos.server.controller.game.enums;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.ChooseCardPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.ExtraDrawPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.MoveTotemPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.SkipExtraDrawPayload;

/**
 * Supported player commands that can be dispatched to a running game.
 */
public enum GameCommandType {
    /**
     * Command for choosing a tribe card from the board.
     */
    CHOOSE_CARD,

    /**
     * Command for resolving an extra draw effect.
     */
    EXTRA_DRAW,

    /**
     * Command for placing or moving a player's totem.
     */
    MOVE_TOTEM,

    SKIP_EXTRA_DRAW;

    /**
     * Maps an incoming game payload to the command handled by the game controller.
     *
     * @param payload payload received from the client
     * @return command type represented by the payload
     * @throws IllegalArgumentException if the payload is null or its type is unrecognized
     */
    public static GameCommandType fromPayload(ClientPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        return switch (payload) {
            case ChooseCardPayload ignored -> CHOOSE_CARD;
            case ExtraDrawPayload ignored -> EXTRA_DRAW;
            case MoveTotemPayload ignored -> MOVE_TOTEM;
            case SkipExtraDrawPayload ignored ->SKIP_EXTRA_DRAW;
            default -> throw new IllegalArgumentException("Unexpected game payload: " + payload);
        };
    }
}
