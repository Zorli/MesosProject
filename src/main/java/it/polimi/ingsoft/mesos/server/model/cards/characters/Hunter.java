package it.polimi.ingsoft.mesos.server.model.cards.characters;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;

public class Hunter extends CharacterCard {
    private final boolean hasEffect;
    public Hunter(int id, Era era, boolean hasEffect, TribeCardType type) {
        super(id, era, type);
        this.hasEffect = hasEffect;
    }
    public boolean getHasEffect() {
        return this.hasEffect;
    }
}
