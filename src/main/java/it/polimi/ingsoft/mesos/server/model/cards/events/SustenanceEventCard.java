package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.SustenanceDiscount;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;

import java.util.ArrayList;

public class SustenanceEventCard extends EventCard {
    public final int prestigePointsToRemovePerCharacter;

    public SustenanceEventCard(int id, Era era, TribeCardType type, int prestigePointsToRemovePerCharacter) {
        super(id, era,type);
        this.prestigePointsToRemovePerCharacter = prestigePointsToRemovePerCharacter;
    }

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
}
