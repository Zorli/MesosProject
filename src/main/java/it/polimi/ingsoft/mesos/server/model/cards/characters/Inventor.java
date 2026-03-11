package it.polimi.ingsoft.mesos.server.model.cards.characters;

import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.InventionType;

public class Inventor extends TribeCard {
    private final InventionType inventionType;
    public Inventor(int id, Era era, InventionType inventionType) {
        super(id, era);
        this.inventionType = inventionType;
    }

    public InventionType getInventionType() {
        return this.inventionType;
    }
}
