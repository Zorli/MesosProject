package it.polimi.ingsoft.mesos.server.model.cards.characters;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;

public class Hunter extends TribeCard {
    private final boolean hasEffect;
    public Hunter(int id, Era era, boolean hasEffect) {
        super(id, era);
        this.hasEffect = hasEffect;
    }
    public boolean getHasEffect() {
        return this.hasEffect;
    }
}
