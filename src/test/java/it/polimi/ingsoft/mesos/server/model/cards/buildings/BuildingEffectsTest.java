package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.*;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Artist;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Builder;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.CharacterCard;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BuildingEffectsTest {

    @Test
    void setForFoodRewardsCompletedSetsAndPreservesExtraCopies() {
        Player player = createPlayer(0, 0);
        SetForFood building = new SetForFood(
                1,
                Era.first,
                3,
                1,
                BenefitType.POST_DRAW_SET_FOR_FOOD,
                4
        );

        building.addCharacterTypeAndApplyEffect(TribeCardType.HUNTER, player);
        building.addCharacterTypeAndApplyEffect(TribeCardType.HUNTER, player);
        building.addCharacterTypeAndApplyEffect(TribeCardType.BUILDER, player);
        building.addCharacterTypeAndApplyEffect(TribeCardType.GATHERER, player);
        building.addCharacterTypeAndApplyEffect(TribeCardType.ARTIST, player);
        building.addCharacterTypeAndApplyEffect(TribeCardType.INVENTOR, player);
        building.addCharacterTypeAndApplyEffect(TribeCardType.SHAMAN, player);

        assertEquals(4, player.getFood());
        assertFalse(building.hasBonus());

        building.addCharacterTypeAndApplyEffect(TribeCardType.BUILDER, player);
        building.addCharacterTypeAndApplyEffect(TribeCardType.GATHERER, player);
        building.addCharacterTypeAndApplyEffect(TribeCardType.ARTIST, player);
        building.addCharacterTypeAndApplyEffect(TribeCardType.INVENTOR, player);
        building.addCharacterTypeAndApplyEffect(TribeCardType.SHAMAN, player);

        assertEquals(8, player.getFood());
    }

    @Test
    void setForFoodDecrementDoesNotGoBelowZero() {
        Player player = createPlayer(0, 0);
        SetForFood building = new SetForFood(
                9,
                Era.first,
                3,
                1,
                BenefitType.POST_DRAW_SET_FOR_FOOD,
                4
        );

        building.addCharacterTypeAndApplyEffect(TribeCardType.HUNTER, player);
        building.decrementCharacterType(TribeCardType.HUNTER);
        building.decrementCharacterType(TribeCardType.HUNTER);

        building.addCharacterTypeAndApplyEffect(TribeCardType.HUNTER, player);
        building.addCharacterTypeAndApplyEffect(TribeCardType.BUILDER, player);
        building.addCharacterTypeAndApplyEffect(TribeCardType.GATHERER, player);
        building.addCharacterTypeAndApplyEffect(TribeCardType.ARTIST, player);
        building.addCharacterTypeAndApplyEffect(TribeCardType.INVENTOR, player);
        building.addCharacterTypeAndApplyEffect(TribeCardType.SHAMAN, player);

        assertEquals(4, player.getFood());
    }

    @Test
    void pairOfInventorRewardsOnlyWhenTheSecondMatchingInventorArrives() {
        Player player = createPlayer(0, 0);
        PairOfInventor building = new PairOfInventor(
                3,
                Era.first,
                2,
                1,
                BenefitType.POST_DRAW_PAIR_OF_INVENTOR_WITH_SAME_INVENTION,
                3
        );

        building.addInventionTypeAndApplyEffect(InventionType.BOW, player);
        building.addInventionTypeAndApplyEffect(InventionType.FIRE, player);
        building.addInventionTypeAndApplyEffect(InventionType.BOW, player);

        assertEquals(3, player.getFood());

        building.addInventionTypeAndApplyEffect(InventionType.FIRE, player);

        assertEquals(6, player.getFood());
        assertEquals(3, building.getRewardFood());
    }

    @Test
    void sustenanceDiscountCountsOnlyMatchingCharacters() {
        SustenanceDiscount building = new SustenanceDiscount(
                5,
                Era.first,
                2,
                1,
                BenefitType.EVENT_BONUS_SUSTENANCE_DISCOUNT_FOR_TYPE,
                2,
                TribeCardType.ARTIST
        );

        List<CharacterCard> characters = List.of(
                new Artist(10, Era.first, TribeCardType.ARTIST),
                new Artist(11, Era.first, TribeCardType.ARTIST),
                new Artist(14, Era.first, TribeCardType.ARTIST),
                new Hunter(12, Era.first, false, TribeCardType.HUNTER),
                new Inventor(13, Era.first, TribeCardType.INVENTOR, InventionType.BASKET)
        );

        assertEquals(6, building.getDiscountAmount(characters));
        assertEquals(2, building.getDiscountAmount());
        assertEquals(TribeCardType.ARTIST, building.getApplicableTribeCardType());
    }

    @Test
    void hunterBonusAppliesFoodAndPrestigePerHunter() {
        Player player = createPlayer(1, 2);
        player.getTribe().addCharacter(new Hunter(20, Era.first, false, TribeCardType.HUNTER));
        player.getTribe().addCharacter(new Hunter(21, Era.first, false, TribeCardType.HUNTER));
        player.getTribe().addCharacter(new Gatherer(22, Era.first, TribeCardType.GATHERER));

        HunterBonus building = new HunterBonus(
                6,
                Era.first,
                2,
                1,
                BenefitType.EVENT_BONUS_HUNTER,
                3,
                2
        );

        building.applyBonusHunterEvent(player);

        assertEquals(7, player.getFood());
        assertEquals(6, player.getPrestigePoints());
        assertEquals(3, building.getBonusFoodPerHunter());
        assertEquals(2, building.getBonusPrestigePointsPerHunter());
    }

    @Test
    void cavePaintingBonusAppliesFoodPerArtistOnly() {
        Player player = createPlayer(2, 0);
        player.getTribe().addCharacter(new Artist(30, Era.first, TribeCardType.ARTIST));
        player.getTribe().addCharacter(new Artist(31, Era.first, TribeCardType.ARTIST));
        player.getTribe().addCharacter(new Builder(32, Era.first, TribeCardType.BUILDER, 1, 0));

        CavePaintingBonus building = new CavePaintingBonus(
                7,
                Era.first,
                2,
                1,
                BenefitType.EVENT_BONUS_CAVE_PAINTING,
                2
        );

        building.applyBonusCavePaintingEvent(player);

        assertEquals(6, player.getFood());
        assertEquals(2, building.getBonusFoodPerArtist());
    }

    @Test
    void shamanStarsBonusContributesToTotalTribeStars() {
        Player player = createPlayer(0, 0);
        player.getTribe().addCharacter(new Shaman(40, Era.first, 2, TribeCardType.SHAMAN));
        player.getTribe().addCharacter(new Shaman(41, Era.first, 1, TribeCardType.SHAMAN));
        player.getTribe().addBuilding(new ShamanStarsBonus(
                8,
                Era.first,
                2,
                1,
                BenefitType.EVENT_BONUS_STARS_SHAMAN,
                3
        ));

        assertEquals(6, player.getTribe().getShamansStars());
    }

    @Test
    void multiplierForTypeRewardsOnlyMatchingCharacters() {
        Player player = createPlayer(0, 1);
        player.getTribe().addCharacter(new Builder(50, Era.first, TribeCardType.BUILDER, 1, 0));
        player.getTribe().addCharacter(new Builder(51, Era.first, TribeCardType.BUILDER, 2, 0));
        player.getTribe().addCharacter(new Artist(52, Era.first, TribeCardType.ARTIST));

        MultiplierForType building = new MultiplierForType(
                15,
                Era.first,
                2,
                1,
                BenefitType.FINAL_BONUS_MULTIPLIER_FOR_TYPE,
                3,
                TribeCardType.BUILDER
        );

        building.applyEffect(player);

        assertEquals(7, player.getPrestigePoints());
    }

    @Test
    void bigPpRewardAddsItsFlatPrestigeBonus() {
        Player player = createPlayer(0, 2);
        BigPpReward building = new BigPpReward(
                16,
                Era.first,
                2,
                1,
                BenefitType.FINAL_BONUS_BIG_PP_REWARD,
                5
        );

        building.applyEffect(player);

        assertEquals(7, player.getPrestigePoints());
    }

    @Test
    void prestigePointsForSetRewardsEachCompleteCharacterSet() {
        Player player = createPlayer(0, 1);
        player.getTribe().addCharacter(new Hunter(60, Era.first, false, TribeCardType.HUNTER));
        player.getTribe().addCharacter(new Hunter(61, Era.first, false, TribeCardType.HUNTER));
        player.getTribe().addCharacter(new Builder(62, Era.first, TribeCardType.BUILDER, 1, 0));
        player.getTribe().addCharacter(new Builder(63, Era.first, TribeCardType.BUILDER, 1, 0));
        player.getTribe().addCharacter(new Gatherer(64, Era.first, TribeCardType.GATHERER));
        player.getTribe().addCharacter(new Gatherer(65, Era.first, TribeCardType.GATHERER));
        player.getTribe().addCharacter(new Artist(66, Era.first, TribeCardType.ARTIST));
        player.getTribe().addCharacter(new Artist(67, Era.first, TribeCardType.ARTIST));
        player.getTribe().addCharacter(new Inventor(68, Era.first, TribeCardType.INVENTOR, InventionType.BOW));
        player.getTribe().addCharacter(new Inventor(69, Era.first, TribeCardType.INVENTOR, InventionType.FIRE));
        player.getTribe().addCharacter(new Shaman(70, Era.first, 1, TribeCardType.SHAMAN));
        player.getTribe().addCharacter(new Shaman(71, Era.first, 2, TribeCardType.SHAMAN));
        player.getTribe().addCharacter(new Hunter(72, Era.first, false, TribeCardType.HUNTER));

        PrestigePointsForSet building = new PrestigePointsForSet(
                17,
                Era.first,
                2,
                1,
                BenefitType.FINAL_PRESTIGE_POINTS_FOR_SET,
                4
        );

        building.applyEffect(player);

        assertEquals(9, player.getPrestigePoints());
    }

    private Player createPlayer(int food, int prestigePoints) {
        return new Player("Alice", food, prestigePoints, TotemColor.RED, new Tribe());
    }
}
