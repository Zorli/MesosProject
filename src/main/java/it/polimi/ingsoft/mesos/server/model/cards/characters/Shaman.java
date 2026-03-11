package it.polimi.ingsoft.mesos.server.model.cards.characters;

import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;

public class Shaman extends TribeCard {
    private final int stars;
    public Shaman(int id, Era era, int stars) {
        super(id, era);
        this.stars = stars;
    }
    public int getStars(){
        return this.stars;
    }
}
