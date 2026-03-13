package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;

import java.util.ArrayList;

public class SustenanceEventCard extends TribeCard implements EventAction {
    public final int prestigePointsToRemovePerCharacter;

    public SustenanceEventCard(int id, Era era, int prestigePointsToRemovePerCharacter) {
        super(id, era);
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
            }
            // TODO: sconto ulteriore dall'edificio che fa pagare meno cibo per il sostentamento

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
