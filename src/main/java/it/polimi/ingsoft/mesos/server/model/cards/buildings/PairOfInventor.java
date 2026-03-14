package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.InventionType;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.HashMap;
import java.util.Map;

public class PairOfInventor extends BuildingCard {
    private final int rewardFood;
    private final Map<InventionType, Integer> inventionTypeIntegerMap;

    public PairOfInventor(int id, Era era, int fc, int pp, BenefitType typeOfBenefit, int rewardFood) {
        super(id, era, fc, pp, typeOfBenefit);
        this.rewardFood = rewardFood;
        this.inventionTypeIntegerMap = new HashMap<>();
        for (InventionType type : InventionType.values()) {
            this.inventionTypeIntegerMap.put(type, 0);
        }
    }

    public void addInventionTypeAndApplyEffect(InventionType type, Player player) {
        if (this.inventionTypeIntegerMap.containsKey(type)) {
            this.inventionTypeIntegerMap.put(type, this.inventionTypeIntegerMap.get(type) + 1);
            if (this.inventionTypeIntegerMap.get(type) == 2) {
                this.inventionTypeIntegerMap.put(type, 0);
                player.gainFood(this.rewardFood);
            }
        }
    }
}