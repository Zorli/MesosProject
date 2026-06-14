package it.polimi.ingsoft.mesos.server.model.game.cards.characters;

import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;

/**
 * Character card representing a builder.
 *
 * <p>Builders contribute a purchase discount and may also award prestige points during final
 * scoring.
 */
public class Builder extends CharacterCard {
    private final int priceDiscount;
    private final int prestigePoints;

    /**
     * Creates a builder card.
     *
     * @param id card identifier
     * @param era card era
     * @param type tribe-card type
     * @param pd building-price discount granted by the builder
     * @param pp prestige points contributed by the builder during final scoring
     */
    public Builder(int id, Era era, TribeCardType type, int pd, int pp){
        super(id, era, type);
        this.priceDiscount = pd;
        this.prestigePoints = pp;
    }

    /**
     * Returns the discount this builder contributes to eligible building costs.
     *
     * @return builder discount amount
     */
    public int getPriceDiscount(){
        return this.priceDiscount;
    }

    /**
     * Returns the prestige points this builder contributes during final scoring.
     *
     * @return builder prestige-point contribution
     */
    public int getPrestigePoints(){
        return this.prestigePoints;
    }

}
