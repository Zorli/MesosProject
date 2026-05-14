package it.polimi.ingsoft.mesos.server.model.game.cards.events;

import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.SustenanceDiscount;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

import java.util.ArrayList;

/**
 * Event card that forces players to pay food for sustenance and converts any
 * remaining shortage into prestige point loss.
 */
public class SustenanceEventCard extends EventCard {
    public final int prestigePointsToRemovePerCharacter;

    /**
     * Creates the event card.
     *
     * @param id card identifier
     * @param era card era
     * @param type tribe card type, expected to be {@code EVENT}
     * @param prestigePointsToRemovePerCharacter prestige point penalty applied to each unpaid sustenance unit
     */
    public SustenanceEventCard(int id, Era era, TribeCardType type, int prestigePointsToRemovePerCharacter) {
        super(id, era,type);
        this.prestigePointsToRemovePerCharacter = prestigePointsToRemovePerCharacter;
    }

    /**
     * Applies the event to all players in the match.
     *
     * @param players players affected by the event
     */
    @Override
    public void applyEffect(ArrayList<Player> players) {
        for (Player player : players) {
            int numOfGatherers = player.getTribe().countGatherers();
            int numOfCharacters = player.getTribe().getCharactersSize();
            int totalToRemove = numOfCharacters - numOfGatherers * 3;
            if (totalToRemove < 0) {
                totalToRemove = 0;
            } else {
                for (BuildingCard b : player.getTribe().getBuildings()) {
                    if (b.getBenefitType() == BenefitType.EVENT_BONUS_SUSTENANCE_DISCOUNT_FOR_TYPE) {
                        totalToRemove -= ((SustenanceDiscount) b).getDiscountAmount(player.getTribe().getCharacters());
                    }
                }
                if(totalToRemove<0){
                    totalToRemove = 0;
                }
            }

            int multiplierForNotEnoughFood = totalToRemove - player.getFood();
            if (multiplierForNotEnoughFood <= 0) {
                multiplierForNotEnoughFood = 0;
            } else {
                totalToRemove -= multiplierForNotEnoughFood;
            }
            player.payFood(totalToRemove);
            player.payPrestigePoints(multiplierForNotEnoughFood * this.prestigePointsToRemovePerCharacter);
        }

    }

    /**
     * Returns the prestige point penalty applied to each unpaid sustenance unit.
     *
     * @return prestige point penalty multiplier
     */
    public int getPrestigePointsToRemovePerCharacter() {
        return this.prestigePointsToRemovePerCharacter;
    }
}
