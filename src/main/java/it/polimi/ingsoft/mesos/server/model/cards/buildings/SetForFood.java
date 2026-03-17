package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Building that rewards food when the owner completes a set containing all
 * distinct tribe character types.
 */
public class SetForFood extends BuildingCard {
    private final int rewardFood;
    private final Map<TribeCardType, Integer> characterTypeCountMap;

    /**
     * Creates the building and initializes one counter for each non-event tribe type.
     *
     * @param id card identifier
     * @param era card era
     * @param fc food cost
     * @param pp prestige points printed on the building
     * @param typeOfBenefit effect represented by the building
     * @param rewardFood food granted when a full set is completed
     */
    public SetForFood(int id, Era era, int fc, int pp, BenefitType typeOfBenefit, int rewardFood) {
        super(id, era, fc, pp, typeOfBenefit);
        this.rewardFood = rewardFood;
        this.characterTypeCountMap = new HashMap<>();
        for (TribeCardType type : TribeCardType.values()) {
            // now the map has only the character types
            if(type != TribeCardType.EVENT) {
                this.characterTypeCountMap.put(type, 0);
            }
        }
    }

    /**
     * Registers a newly acquired character type and applies the reward when the
     * set contains every character type at least once.
     *
     * @param type type of the character just acquired
     * @param player player owning the building
     */
    public void addCharacterTypeAndApplyEffect(TribeCardType type, Player player) {
        if (this.characterTypeCountMap.containsKey(type)) {
            this.characterTypeCountMap.put(type, this.characterTypeCountMap.get(type) + 1);
        }
        if(hasBonus()) {
            decrementAllCharacterTypes();
            player.gainFood(this.rewardFood);
        }
    }

    /**
     * Checks whether the tracked set currently contains all character types.
     *
     * @return {@code true} if every non-event tribe type has count greater than zero
     */
    public boolean hasBonus() {
        return this.characterTypeCountMap.values().stream()
                .allMatch(count -> count > 0);
    }

    /**
     * Decrements the counter associated with a single character type.
     *
     * @param type character type to decrement
     */
    public void decrementCharacterType(TribeCardType type) {
        if (this.characterTypeCountMap.containsKey(type)) {
            int currentCount = this.characterTypeCountMap.get(type);
            if (currentCount > 0) {
                this.characterTypeCountMap.put(type, currentCount - 1);
            }
        }
    }

    /**
     * Decrements every tracked character type by one when possible.
     */
    public void decrementAllCharacterTypes() {
        for (TribeCardType type : TribeCardType.values()) {
            decrementCharacterType(type);
        }
    }

    /**
     * Returns the current internal counters for each tracked tribe-card type.
     *
     * @return copy of the mutable counters needed for exact restore
     */
    @Override
    public Map<String, Integer> exportRuntimeState() {
        Map<String, Integer> exportedState = new HashMap<>();
        for (Map.Entry<TribeCardType, Integer> entry : this.characterTypeCountMap.entrySet()) {
            exportedState.put(entry.getKey().name(), entry.getValue());
        }
        return exportedState;
    }

    /**
     * Restores the internal counters used to track partial completed sets.
     *
     * @param runtimeState persisted counters keyed by tribe-card type name
     */
    @Override
    public void restoreRuntimeState(Map<String, Integer> runtimeState) {
        this.characterTypeCountMap.replaceAll((key, value) -> 0);
        if (runtimeState == null) {
            return;
        }

        for (Map.Entry<String, Integer> entry : runtimeState.entrySet()) {
            TribeCardType type = TribeCardType.valueOf(entry.getKey());
            if (this.characterTypeCountMap.containsKey(type)) {
                this.characterTypeCountMap.put(type, entry.getValue());
            }
        }
    }

}
