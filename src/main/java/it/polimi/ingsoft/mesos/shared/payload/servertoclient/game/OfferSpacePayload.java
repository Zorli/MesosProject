package it.polimi.ingsoft.mesos.shared.payload.servertoclient.game;
import java.io.Serializable;
/**
 * Represents the state of a single space on the board's offer path.
 * <p>
 * Each space has details on how many cards can be taken from the top/bottom rows,
 * and which player (by nickname) currently occupies the space with their totem.
 */
public class OfferSpacePayload implements Serializable {
    /** The code/identifier of this offer space on the board. */
    public final String code;

    /** The number of cards a player occupying this space must take from the top row of the board. */
    public final int topRowCardsToTake;

    /** The number of cards a player occupying this space must take from the bottom row of the board. */
    public final int bottomRowCardsToTake;

    /** The nickname of the player whose totem currently occupies this space (null if empty). */
    public final String occupiedByNickname;

    /**
     * Constructs a new OfferSpacePayload.
     *
     * @param code the identifier code of the offer space
     * @param topRowCardsToTake number of cards to take from the top row
     * @param bottomRowCardsToTake number of cards to take from the bottom row
     * @param occupiedByNickname nickname of the player occupying this space, or null
     */
    public OfferSpacePayload(
            String code,
            int topRowCardsToTake,
            int bottomRowCardsToTake,
            String occupiedByNickname
    ) {
        this.code = code;
        this.topRowCardsToTake = topRowCardsToTake;
        this.bottomRowCardsToTake = bottomRowCardsToTake;
        this.occupiedByNickname = occupiedByNickname;
    }
}
