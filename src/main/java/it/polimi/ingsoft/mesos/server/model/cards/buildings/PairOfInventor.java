package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.InventionType;
import it.polimi.ingsoft.mesos.server.model.player.Player;

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

    /**
     * Returns the current internal counters for each invention type.
     *
     * @return copy of the mutable counters needed for exact restore
     */
    @Override
    public Map<String, Integer> exportRuntimeState() {
        Map<String, Integer> exportedState = new HashMap<>();
        for (Map.Entry<InventionType, Integer> entry : this.inventionTypeIntegerMap.entrySet()) {
            exportedState.put(entry.getKey().name(), entry.getValue());
        }
        return exportedState;
    }

    /**
     * Restores the internal counters used to track partial inventor pairs.
     *
     * @param runtimeState exported counters keyed by invention type name
     */
    @Override
    public void restoreRuntimeState(Map<String, Integer> runtimeState) {
        this.inventionTypeIntegerMap.replaceAll((key, value) -> 0);
        if (runtimeState == null) {
            return;
        }

        for (Map.Entry<String, Integer> entry : runtimeState.entrySet()) {
            InventionType type = InventionType.valueOf(entry.getKey());
            if (this.inventionTypeIntegerMap.containsKey(type)) {
                this.inventionTypeIntegerMap.put(type, entry.getValue());
            }
        }
    }
}
