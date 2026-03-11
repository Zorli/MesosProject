package it.polimi.ingsoft.mesos.server.model.cards.characters;

import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.enums.Era;

public class Hunter extends Card {
    private final boolean hasEffect;
    public Hunter(int id, Era era, boolean hasEffect) {
        super(id, era);
        this.hasEffect = hasEffect;
    }
    public boolean getHasEffect() {
        return this.hasEffect;
    }
}

