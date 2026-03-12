package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.CharacterType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;

import java.util.HashMap;
import java.util.Map;

public class SetForFood extends BuildingCard {
    private final int rewardFood;
    private final Map<CharacterType, Integer> characterTypeCountMap;

    public SetForFood(int id, Era era, int fc, int pp, int rewardFood, boolean hasEffectDuringMatch) {
        super(id, era, fc, pp, hasEffectDuringMatch);
        this.rewardFood = rewardFood;
        this.characterTypeCountMap = new HashMap<>();
        for (CharacterType type : CharacterType.values()) {
            this.characterTypeCountMap.put(type, 0);
        }
    }

    
    public void addCharacterType(CharacterType type) {
        if (this.characterTypeCountMap.containsKey(type)) {
            this.characterTypeCountMap.put(type, this.characterTypeCountMap.get(type) + 1);
        }
    }

    
    public boolean hasBonus() {
        return this.characterTypeCountMap.values().stream()
                .allMatch(count -> count > 0);
    }

    public void decrementCharacterType(CharacterType type) {
        if (this.characterTypeCountMap.containsKey(type)) {
            int currentCount = this.characterTypeCountMap.get(type);
            if (currentCount > 0) {
                this.characterTypeCountMap.put(type, currentCount - 1);
            }
        }
    }

    public void decrementAllCharacterTypes() {
        for (CharacterType type : CharacterType.values()) {
            decrementCharacterType(type);
        }
    }

    public int getCharacterTypeCount(CharacterType type) {
        return this.characterTypeCountMap.getOrDefault(type, 0);
    }

    public int getRewardFood() {
        decrementAllCharacterTypes();
        return this.rewardFood;
    }

}
