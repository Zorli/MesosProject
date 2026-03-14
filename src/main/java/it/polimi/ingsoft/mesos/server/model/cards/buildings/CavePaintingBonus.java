package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

public class CavePaintingBonus extends BuildingCard {
    private final int bonusFoodPerArtist;

    public CavePaintingBonus(int id, Era era, int fc, int pp, BenefitType type, int bonusFoodPerArtist) {
        super(id, era, fc, pp, type);
        this.bonusFoodPerArtist = bonusFoodPerArtist;
    }

    public void applyBonusCavePaintingEvent(Player player) {
        int count = player.getTribe().countArtists();
        player.gainFood(count * this.bonusFoodPerArtist);
    }
}
