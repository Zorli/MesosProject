package it.polimi.ingsoft.mesos.server.model.cards;

import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Builder;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.server.model.game.player.Tribe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingCardTest {

    @Test
    void getEffectiveFoodCostAppliesBuilderDiscount() {
        Tribe tribe = new Tribe();
        tribe.addCharacter(new Builder(10, Era.first, TribeCardType.BUILDER, 2, 0));
        tribe.addCharacter(new Builder(11, Era.first, TribeCardType.BUILDER, 1, 0));
        Player player = new Player("Alice", 1, 0, TotemColor.RED, tribe);
        BuildingCard buildingCard = new BuildingCard(1, Era.first, 5, 1, BenefitType.END_ROUND_EXTRA_CARD);

        assertEquals(2, buildingCard.getEffectiveFoodCost(player));
    }

    @Test
    void getEffectiveFoodCostNeverDropsBelowZero() {
        Tribe tribe = new Tribe();
        tribe.addCharacter(new Builder(10, Era.first, TribeCardType.BUILDER, 5, 0));
        Player player = new Player("Alice", 0, 0, TotemColor.RED, tribe);
        BuildingCard buildingCard = new BuildingCard(1, Era.first, 3, 1, BenefitType.END_ROUND_EXTRA_CARD);

        assertEquals(0, buildingCard.getEffectiveFoodCost(player));
        assertEquals(true, buildingCard.isDrawable(player));
    }

    @Test
    void payEffectiveCostUsesDiscountedAmountAndCanSpillIntoPrestigeLoss() {
        Tribe tribe = new Tribe();
        tribe.addCharacter(new Builder(10, Era.first, TribeCardType.BUILDER, 1, 0));
        Player player = new Player("Alice", 2, 5, TotemColor.RED, tribe);
        BuildingCard buildingCard = new BuildingCard(2, Era.second, 4, 3, BenefitType.END_ROUND_EXTRA_CARD);

        buildingCard.payEffectiveCost(player);

        assertEquals(0, player.getFood());
        assertEquals(4, player.getPrestigePoints());
    }

    @Test
    void gettersAndFinalPrestigeMethodsExposeStaticBuildingData() {
        Player player = new Player("Alice", 0, 1, TotemColor.RED, new Tribe());
        BuildingCard buildingCard = new BuildingCard(3, Era.third, 6, 4, BenefitType.FINAL_BONUS_BIG_PP_REWARD);

        buildingCard.increaseFinalPrestigePoints(player);
        buildingCard.applyEffect(player);

        assertEquals(3, buildingCard.getId());
        assertEquals(Era.third, buildingCard.getEra());
        assertEquals(6, buildingCard.getFoodCost());
        assertEquals(4, buildingCard.getPrestigePoints());
        assertEquals(BenefitType.FINAL_BONUS_BIG_PP_REWARD, buildingCard.getBenefitType());
        assertEquals(5, player.getPrestigePoints());
    }

}
