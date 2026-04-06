package it.polimi.ingsoft.mesos.shared.payload;

public class OfferSpacePayload {
    public final String code;
    public final int topRowCardsToTake;
    public final int bottomRowCardsToTake;
    public final String occupiedByNickname;

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
