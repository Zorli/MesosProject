package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.HunterBonus;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.controller.player.Player;

import java.util.ArrayList;

/**
 * Event card that rewards each player according to the number of hunters in
 * their tribe and then applies any matching hunter-building bonus.
 */
public class HuntEventCard extends EventCard {
    private final int foodReward;
    private final int prestigePointsReward;

    /**
     * Creates the event card.
     *
     * @param id card identifier
     * @param era card era
     * @param type tribe card type, expected to be {@code EVENT}
     * @param foodReward food gained per hunter
     * @param prestigePointsReward prestige points gained per hunter
     */
    public HuntEventCard(int id, Era era, TribeCardType type, int foodReward, int prestigePointsReward) {
        super(id, era, type);
        this.foodReward = foodReward;
        this.prestigePointsReward = prestigePointsReward;
    }

    /**
     * Applies the event to all players in the match.
     *
     * @param players players affected by the event
     */
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

    /**
     * Returns the food granted for each hunter.
     *
     * @return food reward per hunter
     */
    public int getFoodReward() {
        return this.foodReward;
    }

    /**
     * Returns the prestige points granted for each hunter.
     *
     * @return prestige point reward per hunter
     */
    public int getPrestigePointsReward() {
        return this.prestigePointsReward;
    }
}
