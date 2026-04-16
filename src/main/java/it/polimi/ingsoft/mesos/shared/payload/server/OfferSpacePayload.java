package it.polimi.ingsoft.mesos.shared.payload.server;
import java.io.Serializable;
public class OfferSpacePayload implements Serializable {
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
