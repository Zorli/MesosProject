package it.polimi.ingsoft.mesos.shared.payload.servertoclient;
import java.io.Serializable;
import java.util.List;

public class TribePayload implements Serializable {
    public final List<CardPayload> characters;
    public final List<CardPayload> buildings;

    public TribePayload(List<CardPayload> characters, List<CardPayload> buildings) {
        this.characters = characters;
        this.buildings = buildings;
    }
}
