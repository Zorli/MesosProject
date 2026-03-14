package it.polimi.ingsoft.mesos.server.model.cards.characters;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.InventionType;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;

public class Inventor extends CharacterCard {
    private final InventionType inventionType;
    public Inventor(int id, Era era, TribeCardType type, InventionType inventionType) {
        super(id, era, type);
        this.inventionType = inventionType;
    }

    public InventionType getInventionType() {
        return this.inventionType;
    }
}
