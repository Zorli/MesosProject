package it.polimi.ingsoft.mesos.server.model.cards.characters;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.controller.player.Player;

/**
 * Character card representing a hunter.
 *
 * <p>Some hunter cards apply an immediate bonus when acquired, rewarding the player according to
 * the number of hunters already present in their tribe.
 */
public class Hunter extends CharacterCard {
    private final boolean hasEffect;

    /**
     * Creates a hunter card.
     *
     * @param id card identifier
     * @param era card era
     * @param hasEffect whether the card triggers the immediate hunter bonus
     * @param type tribe-card type
     */
    public Hunter(int id, Era era, boolean hasEffect, TribeCardType type) {
        super(id, era, type);
        this.hasEffect = hasEffect;
    }

    /**
     * Returns whether this hunter triggers the immediate acquisition effect.
     *
     * @return {@code true} when the hunter has an immediate effect
     */
    public boolean getHasEffect() {
        return this.hasEffect;
    }

    // TODO: We first have to add the card to the player and then call apply the effect !

    /**
     * Applies the hunter immediate effect, rewarding food and prestige points based on the current
     * number of hunters in the player's tribe.
     *
     * @param player player receiving the hunter effect
     */
    public void applyEffect(Player player) {
        if (this.hasEffect) {
            int count = player.getTribe().countHunters();
            player.gainFood(count);
            player.gainPrestigePoints(count);
        }
    }
}
