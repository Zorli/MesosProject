package it.polimi.ingsoft.mesos.server.model.cards;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;

public abstract class TribeCard extends Card {
    private final TribeCardType type;
    public TribeCard(int id, Era era , TribeCardType type) {
        super(id, era);
        this.type = type;
    }
    public TribeCardType getType(){
        return this.type;
    }
}
