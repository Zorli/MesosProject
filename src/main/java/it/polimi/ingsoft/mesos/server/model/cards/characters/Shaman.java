package it.polimi.ingsoft.mesos.server.model.cards.characters;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;

public class Shaman extends CharacterCard {
    private final int stars;
    public Shaman(int id, Era era, int stars, TribeCardType type) {
        super(id, era, type);
        this.stars = stars;
    }
    public int getStars(){
        return this.stars;
    }
}
