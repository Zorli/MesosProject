package it.polimi.ingsoft.mesos.server.model.game.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.InventionType;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Building that rewards food whenever the owner completes a pair of inventors
 * with the same invention type.
 */
public class PairOfInventor extends BuildingCard {
    private final int rewardFood;
    private final Map<InventionType, Integer> inventionTypeIntegerMap;

    /**
     * Creates the building and initializes the counters for each invention type.
     *
     * @param id card identifier
     * @param era card era
     * @param fc food cost
     * @param pp prestige points printed on the building
     * @param typeOfBenefit effect represented by the building
     * @param rewardFood food granted when a pair is completed
     */
    public PairOfInventor(int id, Era era, int fc, int pp, BenefitType typeOfBenefit, int rewardFood) {
        super(id, era, fc, pp, typeOfBenefit);
        this.rewardFood = rewardFood;
        this.inventionTypeIntegerMap = new HashMap<>();
        for (InventionType type : InventionType.values()) {
            this.inventionTypeIntegerMap.put(type, 0);
        }
    }

    /**
     * Registers a newly acquired inventor and applies the building reward when a
     * pair of equal inventions is completed.
     *
     * @param type invention type of the inventor just acquired
     * @param player player owning the building
     */
    public void addInventionTypeAndApplyEffect(InventionType type, Player player) {
        if (this.inventionTypeIntegerMap.containsKey(type)) {
            this.inventionTypeIntegerMap.put(type, this.inventionTypeIntegerMap.get(type) + 1);
            if (this.inventionTypeIntegerMap.get(type) == 2) {
                this.inventionTypeIntegerMap.put(type, 0);
                player.gainFood(this.rewardFood);
            }
        }
    }

    /**
     * Returns the food granted for each completed pair.
     *
     * @return reward food
     */
    public int getRewardFood() {
        return this.rewardFood;
    }

}
