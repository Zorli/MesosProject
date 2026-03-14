package it.polimi.ingsoft.mesos.server.model.cards.characters;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;

public class Gatherer extends CharacterCard {
    public Gatherer(int id, Era era, TribeCardType type) {
        super(id, era, type);
    }
}
