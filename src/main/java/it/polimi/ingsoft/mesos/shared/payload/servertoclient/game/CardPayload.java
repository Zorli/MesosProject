package it.polimi.ingsoft.mesos.shared.payload.servertoclient.game;
import java.io.Serializable;
public class CardPayload implements Serializable {
    public final int id;
    public final String type;
    public final int era;
    //Cost edificio
    public final Integer buildingCost;

    //EffettoCacciatore
    public final Boolean hunterHasEffect;

    //Tipologia perks inventore
    public final String inventorPerk;

    //Stelle dello sciamano
    public final Integer sciamanStars;

    //Builders
    public final Integer discount;
    public final Integer finalPrestigePoints;

    //Evento
    public final String eventCompleteName;


    private CardPayload(int id, String type, int era, Integer buildingCost,
                       Boolean hunterHasEffect, String inventorPerk,
                       Integer sciamanStars, Integer discount, Integer finalPrestigePoints,String eventCompleteName) {
        this.id = id;
        this.type = type;
        this.era = era;
        this.buildingCost = buildingCost;
        this.hunterHasEffect = hunterHasEffect;
        this.inventorPerk = inventorPerk;
        this.sciamanStars = sciamanStars;
        this.discount = discount;
        this.finalPrestigePoints = finalPrestigePoints;
        this.eventCompleteName = eventCompleteName;
    }

    public static CardPayload base(int id, String type, int era) {
        return new CardPayload(id, type, era, null, null, null, null, null, null,null);
    }

    //carte building
    public static CardPayload building(int id, String type, int era, int buildingCost) {
        return new CardPayload(id, type, era, buildingCost, null, null, null, null, null,null);
    }

    //Carte cacciatore
    public static CardPayload hunter(int id, String type, int era, boolean hunterHasEffect) {
        return new CardPayload(id, type, era, null, hunterHasEffect, null, null, null, null,null);
    }

    //carte inventore
    public static CardPayload inventor(int id, String type, int era, String inventorPerk) {
        return new CardPayload(id, type, era, null, null, inventorPerk, null, null, null,null);
    }

    //carte sciamano
    public static CardPayload shaman(int id, String type, int era, int sciamanStars) {
        return new CardPayload(id, type, era, null, null, null, sciamanStars, null, null,null);
    }

    //carte costruttore
    public static CardPayload builder(int id, String type, int era, int discount, int finalPrestigePoint) {
        return new CardPayload(id, type, era, null, null, null, null, discount, finalPrestigePoint,null);
    }

    public static CardPayload event(int id, String type, int era, String eventCompleteName) {
        return new CardPayload(id, type, era, null, null, eventCompleteName, null, null, null,eventCompleteName);
    }
}
