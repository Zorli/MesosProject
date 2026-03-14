package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.HunterBonus;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;

public class HuntEventCard extends EventCard {
    private final int foodReward;
    private final int prestigePointsReward;

    public HuntEventCard(int id, Era era, TribeCardType type, int foodReward, int prestigePointsReward) {
        super(id, era, type);
        this.foodReward = foodReward;
        this.prestigePointsReward = prestigePointsReward;
    }

    @Override
    public void applyEffect(ArrayList<Player> players) {
        for (Player player : players) {
            int hunterCount = player.getTribe().countHunters();
            player.gainPrestigePoints(this.prestigePointsReward * hunterCount);
            player.gainFood(this.foodReward * hunterCount);

            for(BuildingCard b: player.getTribe().getBuildings()){
                if(b.getBenefitType() == BenefitType.EVENT_BONUS_HUNTER){
                    ((HunterBonus) b).applyBonusHunterEvent(player);
                }
            }
        }
    }
}
