package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game;

import java.io.Serializable;
/**
 * Payload sent by a player to request moving their totem on the board.
 * <p>
 * Totem movement determines where the player wants to position themselves
 * on the offer path for the drafting phase of the round.
 */
public class MoveTotemPayload implements Serializable, GameClientPayload {
    /** The target tile identifier on the offer path where the totem should be placed. */
    public final String tile;

    /**
     * Constructs a new MoveTotemPayload.
     *
     * @param tile the identifier of the target board tile
     */
    public MoveTotemPayload(String tile) {
        this.tile = tile;
    }
}
