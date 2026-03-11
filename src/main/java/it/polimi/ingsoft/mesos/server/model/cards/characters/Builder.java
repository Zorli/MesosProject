package it.polimi.ingsoft.mesos.server.model.cards.characters;

import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.enums.Era;

public class Builder extends Card {
    private final int priceDiscount;
    private final int prestigePoints;

    public Builder(int id, Era era, int pd, int pp){
        super(id, era);
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
