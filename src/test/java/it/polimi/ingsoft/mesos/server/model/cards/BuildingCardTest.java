package it.polimi.ingsoft.mesos.server.model.cards;

import it.polimi.ingsoft.mesos.server.model.cards.characters.Builder;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(buildingCard.isDrawable(player));
    }
}
