package it.polimi.ingsoft.mesos.server.controller.player;

import it.polimi.ingsoft.mesos.server.controller.player.Tribe;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TribeTest {

    @Test
    void hasEndRoundExtraCardBuildingReturnsTrueWhenMatchingBuildingIsOwned() {
        Tribe tribe = new Tribe();
        tribe.addBuilding(new BuildingCard(1, Era.first, 2, 1, BenefitType.END_ROUND_EXTRA_CARD));

        assertTrue(tribe.hasEndRoundExtraCardBuilding());
    }

    @Test
    void hasEndRoundExtraCardBuildingReturnsFalseWhenMatchingBuildingIsMissing() {
        Tribe tribe = new Tribe();
        tribe.addBuilding(new BuildingCard(2, Era.first, 2, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD));

        assertFalse(tribe.hasEndRoundExtraCardBuilding());
    }
}
