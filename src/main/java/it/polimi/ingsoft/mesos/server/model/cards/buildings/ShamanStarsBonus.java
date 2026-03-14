package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;

public class ShamanStarsBonus extends BuildingCard {
    private final int starsBonus;

    public ShamanStarsBonus(int id, Era era, int fc, int pp, int starsBonus) {
        super(id, era, fc, pp, null);
        this.starsBonus = starsBonus;
    }

    public int getStarsBonus() {
        return this.starsBonus;
    }
}
