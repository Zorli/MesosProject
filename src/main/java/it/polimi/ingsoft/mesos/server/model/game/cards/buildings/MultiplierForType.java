package it.polimi.ingsoft.mesos.server.model.game.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

/**
 * Building that grants final prestige points proportional to the number of characters of a given
 * type in the tribe.
 */
public class MultiplierForType extends BuildingCard {
    private final int multiplier;
    private final TribeCardType applicableTribeCardType;

    /**
     * Creates the building.
     *
     * @param id card identifier
     * @param era card era
     * @param fc food cost
     * @param pp prestige points printed on the building
     * @param type effect represented by the building
     * @param multiplier prestige points granted for each matching character
     * @param applicableTribeCardType character type counted by the multiplier
     */
    public MultiplierForType(int id, Era era, int fc, int pp, BenefitType type, int multiplier, TribeCardType applicableTribeCardType) {
        super(id, era, fc, pp, type);
        this.multiplier = multiplier;
        this.applicableTribeCardType= applicableTribeCardType;
    }

    /**
     * Applies the final-scoring reward to the given player.
     *
     * @param player player receiving the prestige-point reward
     */
    @Override
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
