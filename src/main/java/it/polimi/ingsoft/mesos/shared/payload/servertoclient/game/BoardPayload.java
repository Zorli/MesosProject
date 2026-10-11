package it.polimi.ingsoft.mesos.shared.payload.servertoclient.game;
import java.io.Serializable;
import java.util.List;

/**
 * Represents the layout and state of the central game board.
 * <p>
 * This includes the upper and lower rows of draftable cards, the set of offer spaces
 * determining draft order and options, and starting blocks for totem setup.
 */
public class BoardPayload implements Serializable {
    /** The list of cards currently available in the upper draft row of the board. */
    public final List<CardPayload> upperRow;

    /** The list of cards currently available in the lower draft row of the board. */
    public final List<CardPayload> lowerRow;

    /** The list of offer spaces containing totem placement information. */
    public final List<OfferSpacePayload> offerSpaces;

    /** The list of starting blocks used for initial setup and turn order determination. */
    public final List<StartingBlockPayload> startingBlocks;

    /**
     * Constructs a new BoardPayload.
     *
     * @param upperRow cards in the upper row
     * @param lowerRow cards in the lower row
     * @param offerSpaces list of offer spaces
     * @param startingBlocks list of starting blocks
     */
    public BoardPayload(
            List<CardPayload> upperRow,
            List<CardPayload> lowerRow,
            List<OfferSpacePayload> offerSpaces,
            List<StartingBlockPayload> startingBlocks
    ) {
        this.upperRow = upperRow;
        this.lowerRow = lowerRow;
        this.offerSpaces = offerSpaces;
        this.startingBlocks = startingBlocks;
    }
}
