package it.polimi.ingsoft.mesos.server.model.cards;

import it.polimi.ingsoft.mesos.server.model.enums.Era;

// L'effetto sarà un ENUM?? forse ha senso farlo...

public class BuildingCard extends TribeCard{
    private int foodCost;
    private int prestigePoints;
    private boolean hasEffectDuringMatch;


    public BuildingCard(int id, Era era, int fc, int pp, boolean hasEffectDuringMatch){
        super(id, era);
        this.foodCost = fc;
        this.prestigePoints = pp;
        this.hasEffectDuringMatch = hasEffectDuringMatch;
    }
}
