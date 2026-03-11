package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;

public class SetForFood extends BuildingCard {
    private int rewardFood;
    // TODO: map for new entries to track the sets that will be completed during the game
    public SetForFood(int id, Era era, int fc, int pp, boolean hasEffectDuringMatch) {
        super(id, era, fc, pp, hasEffectDuringMatch);
    }

    // un metodo per aggiungere un tipo di personaggio
    // un metodo per capire se il bonus è applicabile: booleanHasBonus()
    // un metodo per decrementare la quantità dei vari tipi di personaggio

    // NB se un giocatore possiede questa carta ogni volta che pesca bisogna gestire il possibile bonus
    // e la modifica del tracciamento dei numeri di tipo del personaggio

}
