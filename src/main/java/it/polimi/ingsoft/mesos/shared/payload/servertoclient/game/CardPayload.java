package it.polimi.ingsoft.mesos.shared.payload.servertoclient.game;
import java.io.Serializable;
/**
 * Represents a snapshot payload of a card (Character, Building, or Event) sent to the client.
 * <p>
 * This class uses static factory methods to construct specific card sub-types
 * (e.g. hunter, inventor, shaman, builder, event, building) in a single unified payload.
 */
public class CardPayload implements Serializable {
    /** The unique identifier of the card. */
    public final int id;

    /** The type classification of the card (e.g. "HUNTER", "BUILDING", "EVENT"). */
    public final String type;

    /** The era value of the card (1, 2, or 3). */
    public final int era;

    /** The construction cost of the card if it is a building (null otherwise). */
    public final Integer buildingCost;

    /** Flag indicating whether the hunter card triggers a hunting effect (null otherwise). */
    public final Boolean hunterHasEffect;

    /** The name of the perk associated with the inventor card (null otherwise). */
    public final String inventorPerk;

    /** The number of stars/prestige points for the shaman card (null otherwise). */
    public final Integer sciamanStars;

    /** The construction discount value provided by the builder card (null otherwise). */
    public final Integer discount;

    /** The end-of-game prestige points granted by the builder card (null otherwise). */
    public final Integer finalPrestigePoints;

    /** The prestige points printed on the building card (null otherwise). */
    public final Integer buildingPrestigePoints;

    /** The full name/type of the event card (null otherwise). */
    public final String eventCompleteName;


    /**
     * Private constructor to instantiate card payloads with all optional parameters.
     */
    private CardPayload(int id, String type, int era, Integer buildingCost,
                       Boolean hunterHasEffect, String inventorPerk,
                       Integer sciamanStars, Integer discount, Integer finalPrestigePoints,String eventCompleteName,
                       Integer buildingPrestigePoints) {
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
        this.buildingPrestigePoints = buildingPrestigePoints;
    }

    /**
     * Factory method to create a basic card payload.
     *
     * @param id the card id
     * @param type the type string
     * @param era the era index
     * @return the constructed CardPayload
     */
    public static CardPayload base(int id, String type, int era) {
        return new CardPayload(id, type, era, null, null, null, null, null, null,null, null);
    }

    /**
     * Factory method to create a building card payload.
     *
     * @param id the card id
     * @param type the type string
     * @param era the era index
     * @param buildingCost the construction cost of the building
     * @return the constructed CardPayload
     */
    public static CardPayload building(int id, String type, int era, int buildingCost, int buildingPrestigePoints) {
        return new CardPayload(id, type, era, buildingCost, null, null, null, null, null,null,
                buildingPrestigePoints);
    }

    /**
     * Factory method to create a hunter card payload.
     *
     * @param id the card id
     * @param type the type string
     * @param era the era index
     * @param hunterHasEffect true if the hunter has a resolution effect
     * @return the constructed CardPayload
     */
    public static CardPayload hunter(int id, String type, int era, boolean hunterHasEffect) {
        return new CardPayload(id, type, era, null, hunterHasEffect, null, null, null, null,null,null);
    }

    /**
     * Factory method to create an inventor card payload.
     *
     * @param id the card id
     * @param type the type string
     * @param era the era index
     * @param inventorPerk the perk name
     * @return the constructed CardPayload
     */
    public static CardPayload inventor(int id, String type, int era, String inventorPerk) {
        return new CardPayload(id, type, era, null, null, inventorPerk, null, null, null,null,null);
    }

    /**
     * Factory method to create a shaman card payload.
     *
     * @param id the card id
     * @param type the type string
     * @param era the era index
     * @param sciamanStars number of shaman stars
     * @return the constructed CardPayload
     */
    public static CardPayload shaman(int id, String type, int era, int sciamanStars) {
        return new CardPayload(id, type, era, null, null, null, sciamanStars, null, null,null,null);
    }

    /**
     * Factory method to create a builder card payload.
     *
     * @param id the card id
     * @param type the type string
     * @param era the era index
     * @param discount the construction discount
     * @param finalPrestigePoint the final prestige points
     * @return the constructed CardPayload
     */
    public static CardPayload builder(int id, String type, int era, int discount, int finalPrestigePoint) {
        return new CardPayload(id, type, era, null, null, null, null, discount, finalPrestigePoint,null,null);
    }

    /**
     * Factory method to create an event card payload.
     *
     * @param id the card id
     * @param type the type string
     * @param era the era index
     * @param eventCompleteName the full name/description of the event
     * @return the constructed CardPayload
     */
    public static CardPayload event(int id, String type, int era, String eventCompleteName) {
        return new CardPayload(id, type, era, null, null, null, null, null, null,eventCompleteName,null);
    }
}
