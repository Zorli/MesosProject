package it.polimi.ingsoft.mesos.shared.payload.servertoclient.game;
import java.io.Serializable;
/**
 * Represents the state of a single starting block on the game board.
 * <p>
 * Starting blocks determine player order and initial resources (food and prestige)
 * before players begin moving their totems onto the offer spaces.
 */
public class StartingBlockPayload implements Serializable {
    /** The turn-order position of this starting block, starting from 1. */
    public final int position;

    /** The food adjustment modifier granted by this starting block. */
    public final int foodModifier;

    /** The prestige points adjustment modifier granted by this starting block. */
    public final int prestigeModifier;

    /** The nickname of the player occupying this starting block (null if empty). */
    public final String occupiedByNickname;

    /**
     * Constructs a new StartingBlockPayload.
     *
     * @param position the index/position of the starting block
     * @param foodModifier the food modifier value
     * @param prestigeModifier the prestige points modifier
     * @param occupiedByNickname the occupying player's nickname, or null
     */
    public StartingBlockPayload(
            int position,
            int foodModifier,
            int prestigeModifier,
            String occupiedByNickname
    ) {
        this.position = position;
        this.foodModifier = foodModifier;
        this.prestigeModifier = prestigeModifier;
        this.occupiedByNickname = occupiedByNickname;
    }
}
