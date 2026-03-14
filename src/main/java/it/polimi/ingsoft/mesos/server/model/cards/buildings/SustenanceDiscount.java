package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;

import java.util.ArrayList;

public class SustenanceDiscount extends BuildingCard {
    private final int discountAmount;
    private final TribeCardType applicableTribeCardType;

    public SustenanceDiscount(int id, Era era, int fc, int pp, BenefitType typeOfBenefit, int discountAmount, TribeCardType applicableTribeCardType) {
        super(id, era, fc, pp, typeOfBenefit);
        this.discountAmount = discountAmount;
        this.applicableTribeCardType = applicableTribeCardType;
    }

    public int getDiscountAmount(ArrayList<CharacterCard> characters) {
        int count = 0;
        for (CharacterCard character : characters) {
            if (character.getType() == this.applicableTribeCardType) {
                count++;
            }
        }
        return count * this.discountAmount;

    }
}
