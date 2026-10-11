package it.polimi.ingsoft.mesos.server.model.game.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

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

}
