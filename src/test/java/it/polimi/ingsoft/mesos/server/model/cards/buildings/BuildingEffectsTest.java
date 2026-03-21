package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.characters.Artist;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Builder;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Hunter;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Inventor;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Shaman;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.InventionType;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
        assertEquals(1, building.exportRuntimeState().get("HUNTER"));
        assertEquals(0, building.exportRuntimeState().get("BUILDER"));
        assertEquals(0, building.exportRuntimeState().get("GATHERER"));
        assertEquals(0, building.exportRuntimeState().get("ARTIST"));
        assertEquals(0, building.exportRuntimeState().get("INVENTOR"));
        assertEquals(0, building.exportRuntimeState().get("SHAMAN"));
    }

    @Test
    void setForFoodRestoresPartialProgressAndCompletesTheMissingType() {
        Player player = createPlayer(1, 0);
        SetForFood building = new SetForFood(
                2,
                Era.first,
                3,
                1,
                BenefitType.POST_DRAW_SET_FOR_FOOD,
                5
        );

        building.restoreRuntimeState(Map.of(
                "HUNTER", 1,
                "BUILDER", 1,
                "GATHERER", 1,
                "ARTIST", 1,
                "INVENTOR", 1,
                "SHAMAN", 0
        ));

        building.addCharacterTypeAndApplyEffect(TribeCardType.SHAMAN, player);

        assertEquals(6, player.getFood());
        assertEquals(0, building.exportRuntimeState().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void setForFoodDecrementDoesNotGoBelowZeroAndEmptyRestoreClearsExistingState() {
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

        assertEquals(0, building.exportRuntimeState().get("HUNTER"));

        building.addCharacterTypeAndApplyEffect(TribeCardType.BUILDER, player);
        building.restoreRuntimeState(Map.of());

        assertEquals(0, building.exportRuntimeState().get("BUILDER"));
        assertEquals(0, building.exportRuntimeState().values().stream().mapToInt(Integer::intValue).sum());
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
        assertEquals(0, building.exportRuntimeState().get("BOW"));
        assertEquals(1, building.exportRuntimeState().get("FIRE"));
        assertEquals(3, building.getRewardFood());
    }

    @Test
    void pairOfInventorRestoresPendingPairsBeforeCompletingOne() {
        Player player = createPlayer(2, 0);
        PairOfInventor building = new PairOfInventor(
                4,
                Era.first,
                2,
                1,
                BenefitType.POST_DRAW_PAIR_OF_INVENTOR_WITH_SAME_INVENTION,
                4
        );

        building.restoreRuntimeState(Map.ofEntries(
                Map.entry("BOW", 1),
                Map.entry("FIRE", 0),
                Map.entry("WHEEL", 0),
                Map.entry("KNIFE", 0),
                Map.entry("RAFT", 0),
                Map.entry("BASKET", 0),
                Map.entry("SPEAR", 0),
                Map.entry("TENT", 0),
                Map.entry("NECKLACE", 0),
                Map.entry("PAINTING", 0)
        ));

        building.addInventionTypeAndApplyEffect(InventionType.BOW, player);

        assertEquals(6, player.getFood());
        assertEquals(0, building.exportRuntimeState().get("BOW"));
    }

    @Test
    void pairOfInventorEmptyRestoreClearsExistingCounters() {
        Player player = createPlayer(0, 0);
        PairOfInventor building = new PairOfInventor(
                14,
                Era.first,
                2,
                1,
                BenefitType.POST_DRAW_PAIR_OF_INVENTOR_WITH_SAME_INVENTION,
                4
        );

        building.addInventionTypeAndApplyEffect(InventionType.BOW, player);
        building.addInventionTypeAndApplyEffect(InventionType.FIRE, player);
        building.restoreRuntimeState(Map.of());

        assertEquals(0, building.exportRuntimeState().get("BOW"));
        assertEquals(0, building.exportRuntimeState().get("FIRE"));
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
