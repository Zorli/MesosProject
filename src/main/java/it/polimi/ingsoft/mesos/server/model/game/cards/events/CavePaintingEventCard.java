package it.polimi.ingsoft.mesos.server.model.game.cards.events;

import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.CavePaintingBonus;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

import java.util.ArrayList;

/**
 * Event card that rewards or penalizes players according to the number of
 * artists in their tribe and then applies any matching building bonuses.
 */
public class CavePaintingEventCard extends EventCard {
    private final int limitForPositive;
    private final int prestigePointsForPositive;
    private final int prestigePointsForNegative;

    /**
     * Creates the event card.
     *
     * @param id card identifier
     * @param era card era
     * @param limitForPositive minimum artist count required to avoid the penalty
     * @param type tribe card type, expected to be {@code EVENT}
     * @param prestigePointsForPositive prestige points granted per artist when the threshold is met
     * @param prestigePointsForNegative prestige points lost when the threshold is not met
     */
    public CavePaintingEventCard(int id, Era era, int limitForPositive, TribeCardType type, int prestigePointsForPositive, int prestigePointsForNegative) {
        super(id, era, type);
        this.limitForPositive = limitForPositive;
        this.prestigePointsForPositive = prestigePointsForPositive;
        this.prestigePointsForNegative = prestigePointsForNegative;
    }

    /**
     * Applies the event to all players in the match.
     *
     * @param players players affected by the event
     */
    @Override
    public void applyEffect(ArrayList<Player> players) {
        for (Player player : players) {
            int numOfArtists = player.getTribe().countArtists();
            if (numOfArtists < this.limitForPositive) {
                player.payPrestigePoints(this.prestigePointsForNegative);
            } else {
                player.gainPrestigePoints(this.prestigePointsForPositive * numOfArtists);
            }

            for(BuildingCard b: player.getTribe().getBuildings()){
                if(b.getBenefitType() == BenefitType.EVENT_BONUS_CAVE_PAINTING){
                    ((CavePaintingBonus) b).applyBonusCavePaintingEvent(player);
                }
            }

        }
    }

    /**
     * Returns the artist threshold that turns the event into a positive effect.
     *
     * @return positive threshold
     */
    public int getLimitForPositive() {
        return this.limitForPositive;
    }

    /**
     * Returns the prestige points granted for each artist when the threshold is met.
     *
     * @return positive prestige point multiplier
     */
    public int getPrestigePointsForPositive() {
        return this.prestigePointsForPositive;
    }

    /**
     * Returns the prestige point penalty applied when the threshold is not met.
     *
     * @return negative prestige point value
     */
    public int getPrestigePointsForNegative() {
        return this.prestigePointsForNegative;
    }
}
