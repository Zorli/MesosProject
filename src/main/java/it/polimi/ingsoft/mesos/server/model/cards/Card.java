package it.polimi.ingsoft.mesos.server.model.cards;

import it.polimi.ingsoft.mesos.server.model.enums.Era;

public abstract class Card implements CardAction {
    private final int id;
    private Era era;

    public Card(int id, Era era) {
        this.id = id;
        this.era = era;
    }

    public int getId() {
        return id;
    }

    @Override
    public Era getEra() {
        return this.era;
    }

    public void setEra(Era era) {
        this.era = era;
    }
}
