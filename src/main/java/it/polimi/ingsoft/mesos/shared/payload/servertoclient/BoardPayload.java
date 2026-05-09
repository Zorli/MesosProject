package it.polimi.ingsoft.mesos.shared.payload.servertoclient;
import java.io.Serializable;
import java.util.List;

public class BoardPayload implements Serializable {
    public final List<CardPayload> upperRow;
    public final List<CardPayload> lowerRow;
    public final List<OfferSpacePayload> offerSpaces;
    public final List<StartingBlockPayload> startingBlocks;

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
