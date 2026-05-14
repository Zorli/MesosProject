package it.polimi.ingsoft.mesos.server.model.game.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;

import java.util.List;

/**
 * Building that reduces the sustenance penalty based on how many characters of
 * a given type are present in the tribe.
 */
public class SustenanceDiscount extends BuildingCard {
    private final int discountAmount;
    private final TribeCardType applicableTribeCardType;

    /**
     * Creates the building.
     *
     * @param id card identifier
     * @param era card era
     * @param fc food cost
     * @param pp prestige points printed on the building
     * @param typeOfBenefit effect represented by the building
     * @param discountAmount reduction granted by each matching character
     * @param applicableTribeCardType character type that contributes to the discount
     */
    public SustenanceDiscount(int id, Era era, int fc, int pp, BenefitType typeOfBenefit, int discountAmount, TribeCardType applicableTribeCardType) {
        super(id, era, fc, pp, typeOfBenefit);
        this.discountAmount = discountAmount;
        this.applicableTribeCardType = applicableTribeCardType;
    }

    /**
     * Computes the total sustenance discount produced by the provided characters.
     *
     * @param characters characters currently in the tribe
     * @return total discount amount
     */
    public int getDiscountAmount(List<CharacterCard> characters) {
        int count = 0;
        for (CharacterCard character : characters) {
            if (character.getType() == this.applicableTribeCardType) {
                count++;
            }
        }
        return count * this.discountAmount;

    }

    /**
     * Returns the discount granted by each matching character.
     *
     * @return unit discount amount
     */
    public int getDiscountAmount() {
        return this.discountAmount;
    }

    /**
     * Returns the character type that activates the discount.
     *
     * @return applicable tribe card type
     */
    public TribeCardType getApplicableTribeCardType() {
        return this.applicableTribeCardType;
    }
}
