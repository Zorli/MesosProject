package it.polimi.ingsoft.mesos.server.model.cards.characters;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;

public class Builder extends CharacterCard {
    private final int priceDiscount;
    private final int prestigePoints;

    public Builder(int id, Era era, TribeCardType type, int pd, int pp){
        super(id, era, type);
        this.priceDiscount = pd;
        this.prestigePoints = pp;
    }

    public int getPriceDiscount(){
        return this.priceDiscount;
    }

    public int getPrestigePoints(){
        return this.prestigePoints;
    }

}
