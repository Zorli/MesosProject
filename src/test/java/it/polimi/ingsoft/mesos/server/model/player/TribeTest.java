package it.polimi.ingsoft.mesos.server.model.player;

import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.PairOfInventor;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.SetForFood;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.ShamanStarsBonus;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Artist;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Builder;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Hunter;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Inventor;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Shaman;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.InventionType;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.server.model.game.player.Tribe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TribeTest {

    @Test
    void addCharacterAndBuildingExposeImmutableCollectionsAndCorrectSizes() {
        Tribe tribe = new Tribe();
        tribe.addBuilding(new BuildingCard(1, Era.first, 2, 1, BenefitType.END_ROUND_EXTRA_CARD));
        tribe.addCharacter(new Hunter(1, Era.first, false, TribeCardType.HUNTER));

        assertEquals(1, tribe.getCharactersSize());
        assertEquals(1, tribe.getBuildings().size());
        assertEquals(1, tribe.getCharacters().size());

        assertThrows(UnsupportedOperationException.class, () -> tribe.getBuildings().add(new BuildingCard(2, Era.first, 1, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD)));
        assertThrows(UnsupportedOperationException.class, () -> tribe.getCharacters().add(new Gatherer(3, Era.first, TribeCardType.GATHERER)));
    }

    @Test
    void countCharactersByTypeWorksForEverySupportedClass() {
        Tribe tribe = new Tribe();
        tribe.addCharacter(new Hunter(1, Era.first, true, TribeCardType.HUNTER));
        tribe.addCharacter(new Hunter(2, Era.first, false, TribeCardType.HUNTER));
        tribe.addCharacter(new Shaman(3, Era.first, 2, TribeCardType.SHAMAN));
        tribe.addCharacter(new Shaman(4, Era.first, 1, TribeCardType.SHAMAN));
        tribe.addCharacter(new Artist(5, Era.first, TribeCardType.ARTIST));
        tribe.addCharacter(new Builder(6, Era.first, TribeCardType.BUILDER, 2, 3));
        tribe.addCharacter(new Gatherer(7, Era.first, TribeCardType.GATHERER));

        assertEquals(2, tribe.countHunters());
        assertEquals(1, tribe.countArtists());
        assertEquals(1, tribe.countGatherers());
    }

    @Test
    void addCharacterWithEffectNotifysHunterBonus() {
        Tribe tribe = new Tribe();
        Player player = new Player("Player", 5, 0, TotemColor.RED, tribe);
        tribe.addCharacter(new Hunter(1, Era.first, false, TribeCardType.HUNTER));

        tribe.addCharacter(new Hunter(2, Era.first, true, TribeCardType.HUNTER), player);

        assertEquals(7, player.getFood());
    }

    @Test
    void addCharacterWithNoHunterEffectDoesNotIncreaseFood() {
        Tribe tribe = new Tribe();
        Player player = new Player("Player", 5, 0, TotemColor.BLACK, tribe);

        tribe.addCharacter(new Hunter(1, Era.first, false, TribeCardType.HUNTER), player);

        assertEquals(5, player.getFood());
    }

    @Test
    void postDrawBuildingsAwardFoodForSetAndInventorPair() {
        Tribe tribe = new Tribe();
        Player player = new Player("Player", 0, 0, TotemColor.BLUE, tribe);
        SetForFood setForFood = new SetForFood(10, Era.first, 2, 0, BenefitType.POST_DRAW_SET_FOR_FOOD, 3);
        PairOfInventor pairOfInventor = new PairOfInventor(11, Era.first, 3, 0, BenefitType.POST_DRAW_PAIR_OF_INVENTOR_WITH_SAME_INVENTION, 4);
        tribe.addBuilding(setForFood);
        tribe.addBuilding(pairOfInventor);

        tribe.addCharacter(new Hunter(1, Era.first, false, TribeCardType.HUNTER), player);
        tribe.addCharacter(new Shaman(2, Era.first, 1, TribeCardType.SHAMAN), player);
        tribe.addCharacter(new Artist(3, Era.first, TribeCardType.ARTIST), player);
        tribe.addCharacter(new Builder(4, Era.first, TribeCardType.BUILDER, 0, 0), player);
        tribe.addCharacter(new Gatherer(5, Era.first, TribeCardType.GATHERER), player);
        tribe.addCharacter(new Inventor(6, Era.first, TribeCardType.INVENTOR, InventionType.FIRE), player);

        assertEquals(3, player.getFood());
        tribe.addCharacter(new Inventor(7, Era.first, TribeCardType.INVENTOR, InventionType.FIRE), player);
        assertEquals(7, player.getFood());
    }

    @Test
    void getShamansStarsIncludesBuildingsBonus() {
        Tribe tribe = new Tribe();
        tribe.addCharacter(new Shaman(1, Era.first, 2, TribeCardType.SHAMAN));
        tribe.addCharacter(new Shaman(2, Era.first, 1, TribeCardType.SHAMAN));
        tribe.addBuilding(new ShamanStarsBonus(3, Era.first, 1, 1, BenefitType.EVENT_BONUS_STARS_SHAMAN, 4));

        assertEquals(7, tribe.getShamansStars());
    }

    @Test
    void hasExtraCardBuildingDetectsPresenceAndAbsence() {
        Tribe tribe = new Tribe();
        assertFalse(tribe.hasEndRoundExtraCardBuilding());

        tribe.addBuilding(new BuildingCard(4, Era.first, 1, 1, BenefitType.END_ROUND_EXTRA_CARD));

        assertTrue(tribe.hasEndRoundExtraCardBuilding());
        tribe.addBuilding(new BuildingCard(5, Era.first, 1, 1, BenefitType.END_ROUND_EXTRA_CARD));
        assertTrue(tribe.hasEndRoundExtraCardBuilding());
    }

    @Test
    void hasWinMultiplierIsComputedByBenefits() {
        Tribe tribe = new Tribe();
        assertFalse(tribe.hasWinMultiplier());

        tribe.addBuilding(new BuildingCard(6, Era.first, 1, 1, BenefitType.EVENT_BONUS_SHAMAN_WIN_MULTIPLIER));

        assertTrue(tribe.hasWinMultiplier());
    }

    @Test
    void getTotalFoodDiscountFromBuildersOnlyCountsBuilderCards() {
        Tribe tribe = new Tribe();
        tribe.addCharacter(new Builder(1, Era.first, TribeCardType.BUILDER, 2, 0));
        tribe.addCharacter(new Builder(2, Era.first, TribeCardType.BUILDER, 3, 0));
        tribe.addCharacter(new Hunter(3, Era.first, false, TribeCardType.HUNTER));

        assertEquals(5, tribe.getTotalFoodDiscountFromBuilders());
    }

    @Test
    void getPrestigePointsForBuilderInFinalPhaseAppliesMultiplierBuildingIfPresent() {
        Tribe tribe = new Tribe();
        tribe.addCharacter(new Builder(1, Era.first, TribeCardType.BUILDER, 1, 2));
        tribe.addCharacter(new Builder(2, Era.first, TribeCardType.BUILDER, 1, 1));
        tribe.addBuilding(new BuildingCard(3, Era.first, 1, 1, BenefitType.BUILDER_TOTAL_PP_MULTIPLIER));

        assertEquals(6, tribe.getPrestigePointsForBuilderInFinalPhase());
    }

    @Test
    void getPrestigePointsForArtistsInFinalPhaseAwardsTenPointsEveryTwoArtists() {
        Tribe tribe = new Tribe();
        tribe.addCharacter(new Artist(1, Era.first, TribeCardType.ARTIST));
        tribe.addCharacter(new Artist(2, Era.first, TribeCardType.ARTIST));
        tribe.addCharacter(new Artist(3, Era.first, TribeCardType.ARTIST));

        assertEquals(10, tribe.getPrestigePointsForArtistsInFinalPhase());
    }

    @Test
    void getPrestigePointsForInventorsInFinalPhaseMultipliesInventorsByDifferentInventions() {
        Tribe tribe = new Tribe();
        tribe.addCharacter(new Inventor(1, Era.first, TribeCardType.INVENTOR, InventionType.FIRE));
        tribe.addCharacter(new Inventor(2, Era.first, TribeCardType.INVENTOR, InventionType.FIRE));
        tribe.addCharacter(new Inventor(3, Era.first, TribeCardType.INVENTOR, InventionType.BOW));

        assertEquals(6, tribe.getPrestigePointsForInventorsInFinalPhase());
    }
}
