package it.polimi.ingsoft.mesos.server.model.cards;

import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

// L'effetto sarà un ENUM e lo metto come attributo?? forse ha senso farlo e fare uno switch case in questa classe??

public class BuildingCard extends Card {
    private int foodCost;
    private int prestigePoints;
    private BenefitType benefitType;



    public BuildingCard(int id, Era era, int fc, int pp, BenefitType benefitType){
        super(id, era);
        this.foodCost = fc;
        this.prestigePoints = pp;
        this.benefitType = benefitType;
    }
    @Override
    public boolean isDrawable(Player player) {
        return player.getFood() >= this.foodCost;
    }

    public BenefitType getBenefitType() {
        return this.benefitType;
    }

    public void increaseFinalPrestigePoints(Player player){
        player.gainPrestigePoints(this.prestigePoints);
    }

}
