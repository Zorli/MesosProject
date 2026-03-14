package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.HashMap;
import java.util.Map;

public class SetForFood extends BuildingCard {
    private final int rewardFood;
    private final Map<TribeCardType, Integer> characterTypeCountMap;

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

    public void addCharacterTypeAndApplyEffect(TribeCardType type, Player player) {
        if (this.characterTypeCountMap.containsKey(type)) {
            this.characterTypeCountMap.put(type, this.characterTypeCountMap.get(type) + 1);
        }
        if(hasBonus()) {
            decrementAllCharacterTypes();
            player.gainFood(this.rewardFood);
        }
    }

    public boolean hasBonus() {
        return this.characterTypeCountMap.values().stream()
                .allMatch(count -> count > 0);
    }

    public void decrementCharacterType(TribeCardType type) {
        if (this.characterTypeCountMap.containsKey(type)) {
            int currentCount = this.characterTypeCountMap.get(type);
            if (currentCount > 0) {
                this.characterTypeCountMap.put(type, currentCount - 1);
            }
        }
    }

    public void decrementAllCharacterTypes() {
        for (TribeCardType type : TribeCardType.values()) {
            decrementCharacterType(type);
        }
    }

    public int getCharacterTypeCount(TribeCardType type) {
        return this.characterTypeCountMap.getOrDefault(type, 0);
    }

    public int getRewardFood() {
        decrementAllCharacterTypes();
        return this.rewardFood;
    }

}
