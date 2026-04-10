package it.polimi.ingsoft.mesos.shared.payload.server;

public class CardPayload {
    public final int id;
    public final String type;
    public final int era;

    public CardPayload(int id, String type, int era) {
        this.id = id;
        this.type = type;
        this.era = era;
    }
}
