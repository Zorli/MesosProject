package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;

public class MultiplierForType extends BuildingCard {
    private final int multiplier;
    private final TribeCardType applicableTribeCardType;

    public MultiplierForType(int id, Era era, int fc, int pp, BenefitType type, int multiplier, TribeCardType applicableTribeCardType) {
        super(id, era, fc, pp, type);
        this.multiplier = multiplier;
        this.applicableTribeCardType= applicableTribeCardType;
    }

    public void applyEffect(Player player){
        int count = 0;
        for (CharacterCard card : player.getTribe().getCharacters()) {
            if (card.getType() == this.applicableTribeCardType) {
                count++;
            }
        }
        player.gainPrestigePoints(count * this.multiplier);
    }
}
