package it.polimi.ingsoft.mesos.server.model.cards.characters;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;

public class Hunter extends CharacterCard {
    private final boolean hasEffect;
    public Hunter(int id, Era era, boolean hasEffect, TribeCardType type) {
        super(id, era, type);
        this.hasEffect = hasEffect;
    }
    public boolean getHasEffect() {
        return this.hasEffect;
    }

    // TODO: We first have to add the card to the player and then call apply the effect !
    public void applyEffect(Player player) {
        if (this.hasEffect) {
            int count = player.getTribe().countHunters();
            player.gainFood(count);
            player.gainPrestigePoints(count);
        }
    }
}
