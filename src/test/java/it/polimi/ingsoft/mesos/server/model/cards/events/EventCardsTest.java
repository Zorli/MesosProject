package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.CavePaintingBonus;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.HunterBonus;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.SustenanceDiscount;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Artist;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Hunter;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Shaman;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.events.CavePaintingEventCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.events.HuntEventCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.events.ShamanRitualEventCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.events.SustenanceEventCard;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventCardsTest {

    @Test
    void shamanRitualAppliesWinnerMultiplierAndLossProtection() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        Player carol = player("Carol", TotemColor.WHITE);

        alice.getTribe().addCharacter(new Shaman(1, Era.first, 2, TribeCardType.SHAMAN));
        alice.getTribe().addBuilding(new BuildingCard(100, Era.second, 0, 0, BenefitType.EVENT_BONUS_SHAMAN_WIN_MULTIPLIER));

        bob.getTribe().addCharacter(new Shaman(2, Era.first, 1, TribeCardType.SHAMAN));

        carol.getTribe().addCharacter(new Shaman(3, Era.first, 1, TribeCardType.SHAMAN));
        carol.getTribe().addBuilding(new BuildingCard(101, Era.first, 0, 0, BenefitType.EVENT_BONUS_LOSS_FROM_SHAMAN_EVENT));

        ShamanRitualEventCard eventCard = new ShamanRitualEventCard(87, Era.first, TribeCardType.EVENT, 3, 2);
        eventCard.applyEffect(new ArrayList<>(java.util.List.of(alice, bob, carol)));

        assertEquals(6, alice.getPrestigePoints());
        assertEquals(-2, bob.getPrestigePoints());
        assertEquals(0, carol.getPrestigePoints());
        assertEquals(3, eventCard.getPrestigePointsForHigher());
        assertEquals(2, eventCard.getPrestigePointsForLower());
    }

    @Test
    void shamanRitualAllEqualRewardsEveryoneAndPenalizesOnlyUnprotectedPlayers() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        Player carol = player("Carol", TotemColor.WHITE);

        alice.getTribe().addCharacter(new Shaman(11, Era.first, 1, TribeCardType.SHAMAN));
        alice.getTribe().addBuilding(new BuildingCard(110, Era.first, 0, 0, BenefitType.EVENT_BONUS_SHAMAN_WIN_MULTIPLIER));

        bob.getTribe().addCharacter(new Shaman(12, Era.first, 1, TribeCardType.SHAMAN));

        carol.getTribe().addCharacter(new Shaman(13, Era.first, 1, TribeCardType.SHAMAN));
        carol.getTribe().addBuilding(new BuildingCard(111, Era.first, 0, 0, BenefitType.EVENT_BONUS_LOSS_FROM_SHAMAN_EVENT));

        ShamanRitualEventCard eventCard = new ShamanRitualEventCard(97, Era.first, TribeCardType.EVENT, 3, 2);
        eventCard.applyEffect(new ArrayList<>(java.util.List.of(alice, bob, carol)));

        assertEquals(4, alice.getPrestigePoints());
        assertEquals(1, bob.getPrestigePoints());
        assertEquals(3, carol.getPrestigePoints());
        assertEquals(false, eventCard.isDrawable(alice));
    }

    @Test
    void shamanRitualRewardsHighestPlayerWithoutMultiplierInNonTieScenario() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        Player carol = player("Carol", TotemColor.WHITE);

        alice.getTribe().addCharacter(new Shaman(14, Era.first, 3, TribeCardType.SHAMAN));
        bob.getTribe().addCharacter(new Shaman(15, Era.first, 2, TribeCardType.SHAMAN));
        carol.getTribe().addCharacter(new Shaman(16, Era.first, 1, TribeCardType.SHAMAN));

        ShamanRitualEventCard eventCard = new ShamanRitualEventCard(98, Era.first, TribeCardType.EVENT, 4, 3);
        eventCard.applyEffect(new ArrayList<>(java.util.List.of(alice, bob, carol)));

        assertEquals(4, alice.getPrestigePoints());
        assertEquals(0, bob.getPrestigePoints());
        assertEquals(-3, carol.getPrestigePoints());
    }

    @Test
    void cavePaintingRewardsQualifiedPlayersAndAppliesBuildingFoodBonus() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);

        alice.getTribe().addCharacter(new Artist(1, Era.first, TribeCardType.ARTIST));
        alice.getTribe().addCharacter(new Artist(2, Era.first, TribeCardType.ARTIST));
        alice.getTribe().addBuilding(new CavePaintingBonus(
                108,
                Era.second,
                5,
                6,
                BenefitType.EVENT_BONUS_CAVE_PAINTING,
                1
        ));

        bob.getTribe().addCharacter(new Artist(3, Era.first, TribeCardType.ARTIST));

        CavePaintingEventCard eventCard = new CavePaintingEventCard(88, Era.first, 2, TribeCardType.EVENT, 3, 2);
        eventCard.applyEffect(new ArrayList<>(java.util.List.of(alice, bob)));

        assertEquals(6, alice.getPrestigePoints());
        assertEquals(2, alice.getFood());
        assertEquals(-2, bob.getPrestigePoints());
        assertEquals(0, bob.getFood());
        assertEquals(2, eventCard.getLimitForPositive());
        assertEquals(3, eventCard.getPrestigePointsForPositive());
        assertEquals(2, eventCard.getPrestigePointsForNegative());
    }

    @Test
    void huntRewardsHuntersAndAppliesHunterBuildingBonus() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);

        alice.getTribe().addCharacter(new Hunter(21, Era.first, false, TribeCardType.HUNTER));
        alice.getTribe().addCharacter(new Hunter(22, Era.first, false, TribeCardType.HUNTER));
        alice.getTribe().addCharacter(new Gatherer(23, Era.first, TribeCardType.GATHERER));
        alice.getTribe().addBuilding(new HunterBonus(
                120,
                Era.second,
                4,
                2,
                BenefitType.EVENT_BONUS_HUNTER,
                1,
                2
        ));

        bob.getTribe().addCharacter(new Hunter(24, Era.first, false, TribeCardType.HUNTER));

        HuntEventCard eventCard = new HuntEventCard(89, Era.first, TribeCardType.EVENT, 2, 1);
        eventCard.applyEffect(new ArrayList<>(java.util.List.of(alice, bob)));

        assertEquals(6, alice.getFood());
        assertEquals(6, alice.getPrestigePoints());
        assertEquals(2, bob.getFood());
        assertEquals(1, bob.getPrestigePoints());
        assertEquals(2, eventCard.getFoodReward());
        assertEquals(1, eventCard.getPrestigePointsReward());
    }

    @Test
    void sustenanceConsumesFoodAfterDiscountsAndAppliesPenaltyOnlyToMissingAmount() {
        Player alice = player("Alice", TotemColor.RED);
        alice.gainFood(1);

        alice.getTribe().addCharacter(new Artist(1, Era.first, TribeCardType.ARTIST));
        alice.getTribe().addCharacter(new Artist(2, Era.first, TribeCardType.ARTIST));
        alice.getTribe().addCharacter(new Hunter(3, Era.first, false, TribeCardType.HUNTER));
        alice.getTribe().addCharacter(new Hunter(4, Era.first, false, TribeCardType.HUNTER));
        alice.getTribe().addBuilding(new SustenanceDiscount(
                98,
                Era.first,
                4,
                4,
                BenefitType.EVENT_BONUS_SUSTENANCE_DISCOUNT_FOR_TYPE,
                1,
                TribeCardType.ARTIST
        ));

        SustenanceEventCard eventCard = new SustenanceEventCard(86, Era.first, TribeCardType.EVENT, 2);
        eventCard.applyEffect(new ArrayList<>(java.util.List.of(alice)));

        assertEquals(0, alice.getFood());
        assertEquals(-2, alice.getPrestigePoints());
        assertEquals(2, eventCard.getPrestigePointsToRemovePerCharacter());
    }

    @Test
    void sustenanceClampsNegativeRequirementToZeroWhenGatherersCoverEveryone() {
        Player alice = player("Alice", TotemColor.RED);
        alice.gainFood(2);
        alice.getTribe().addCharacter(new Gatherer(31, Era.first, TribeCardType.GATHERER));
        alice.getTribe().addCharacter(new Artist(32, Era.first, TribeCardType.ARTIST));
        alice.getTribe().addCharacter(new Hunter(33, Era.first, false, TribeCardType.HUNTER));

        SustenanceEventCard eventCard = new SustenanceEventCard(90, Era.first, TribeCardType.EVENT, 3);
        eventCard.applyEffect(new ArrayList<>(java.util.List.of(alice)));

        assertEquals(2, alice.getFood());
        assertEquals(0, alice.getPrestigePoints());
    }

    @Test
    void sustenanceDoesNotPenalizeWhenFoodExactlyMatchesRemainingRequirement() {
        Player alice = player("Alice", TotemColor.RED);
        alice.gainFood(1);
        alice.getTribe().addCharacter(new Artist(41, Era.first, TribeCardType.ARTIST));
        alice.getTribe().addCharacter(new Hunter(42, Era.first, false, TribeCardType.HUNTER));
        alice.getTribe().addBuilding(new SustenanceDiscount(
                99,
                Era.first,
                4,
                4,
                BenefitType.EVENT_BONUS_SUSTENANCE_DISCOUNT_FOR_TYPE,
                1,
                TribeCardType.ARTIST
        ));

        SustenanceEventCard eventCard = new SustenanceEventCard(91, Era.first, TribeCardType.EVENT, 4);
        eventCard.applyEffect(new ArrayList<>(java.util.List.of(alice)));

        assertEquals(0, alice.getFood());
        assertEquals(0, alice.getPrestigePoints());
    }

    private static Player player(String nickname, TotemColor color) {
        Player player = new Player(nickname, 0, 0, color);
        return player;
    }
}
