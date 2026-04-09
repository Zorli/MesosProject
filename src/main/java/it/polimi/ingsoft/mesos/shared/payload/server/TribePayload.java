package it.polimi.ingsoft.mesos.shared.payload.server;

import java.util.List;

public class TribePayload {
    public final List<CardPayload> characters;
    public final List<CardPayload> buildings;

    public TribePayload(List<CardPayload> characters, List<CardPayload> buildings) {
        this.characters = characters;
        this.buildings = buildings;
    }
}
