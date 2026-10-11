package it.polimi.ingsoft.mesos.shared.payload.servertoclient.game;
import java.io.Serializable;
import java.util.List;

/**
 * Represents the cards collected by a player in their tribe board.
 * <p>
 * This is split into character cards (the population) and building cards
 * representing construction achievements.
 */
public class TribePayload implements Serializable {
    /** The list of character card details currently drafted into the player's tribe. */
    public final List<CardPayload> characters;

    /** The list of building card details constructed by the player's tribe. */
    public final List<CardPayload> buildings;

    /**
     * Constructs a new TribePayload.
     *
     * @param characters list of characters in the tribe
     * @param buildings list of buildings in the tribe
     */
    public TribePayload(List<CardPayload> characters, List<CardPayload> buildings) {
        this.characters = characters;
        this.buildings = buildings;
    }
}
