package it.polimi.ingsoft.mesos.server.model.board;

import it.polimi.ingsoft.mesos.server.model.game.board.LowerPile;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.Card;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.game.cards.events.HuntEventCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.events.SustenanceEventCard;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the lower pile as the visible container for cards that moved down from
 * the board. The suite checks observable card storage, discard behavior, and
 * event resolution order.
 */
class LowerPileTest {

    private LowerPile lowerPile;
    private Gatherer basicTribe;
    private BuildingCard basicBuilding;

    @BeforeEach
    void setUp() {
        lowerPile = new LowerPile();
        basicTribe = new Gatherer(1, Era.first, TribeCardType.GATHERER);
        basicBuilding = new BuildingCard(2, Era.first, 2, 1, BenefitType.END_ROUND_EXTRA_CARD);
    }

    /**
     * End-of-round discard removes lower-row tribe cards while keeping lower
     * buildings until the building discard step runs.
     */
    @Test
    void discardLowerRow_And_discardBuildings_AreIndependent() {
        lowerPile.addCard(basicTribe);
        lowerPile.addCard(basicBuilding);

        lowerPile.discardLowerRow();

        assertEquals(0, lowerPile.getTribeCardsSize(), "Tribe cards should have been discarded");
        assertEquals(1, lowerPile.getAllCards().size(), "Building card must survive end-of-round");
        assertTrue(lowerPile.getAllCards().contains(basicBuilding), "The remaining card should be the building");

        lowerPile.discardLowerBuildings();
        assertEquals(0, lowerPile.getAllCards().size(), "Pile should now be completely empty");
    }

    /**
     * Bulk add operations append tribe and building cards to the lower pile
     * without losing either card type.
     */
    @Test
    void addTribeCards_And_addBuildingCards_BulkAddWorks() {
        List<Gatherer> tribes = List.of(basicTribe, new Gatherer(3, Era.first, TribeCardType.GATHERER));
        List<BuildingCard> buildings = List.of(basicBuilding);

        lowerPile.addTribeCards(new ArrayList<>(tribes));
        lowerPile.addBuildingCards(buildings);

        assertEquals(2, lowerPile.getTribeCardsSize(), "Bulk add of tribe cards must work correctly");
        assertEquals(3, lowerPile.getAllCards().size(), "All cards must be present");
    }

    /**
     * Lower-row events resolve regular events before sustenance events, even
     * when the sustenance card was inserted first.
     */
    @Test
    void resolveLowerRowEvents_ExecutesSustenanceLast_RegardlessOfInsertionOrder() {
        List<String> executionLog = new ArrayList<>();
        ArrayList<Player> dummyPlayers = new ArrayList<>();


        HuntEventCard huntEvent = new HuntEventCard(4, Era.first, TribeCardType.EVENT, 1, 1) {
            @Override
            public void applyEffect(ArrayList<Player> players) {
                executionLog.add("HUNT");
            }
        };



        SustenanceEventCard sustenanceEvent = new SustenanceEventCard(5, Era.first, TribeCardType.EVENT, 1) {
            @Override
            public void applyEffect(ArrayList<Player> players) {
                executionLog.add("SUSTENANCE");
            }
        };

        lowerPile.addCard(sustenanceEvent);
        lowerPile.addCard(huntEvent);
        lowerPile.addCard(basicTribe);

        lowerPile.resolveLowerRowEvents(dummyPlayers);

        assertEquals(2, executionLog.size(), "Exactly 2 events should have fired");

        assertEquals("HUNT", executionLog.get(0), "Regular event (Hunt) must fire first");
        assertEquals("SUSTENANCE", executionLog.get(1), "Sustenance event must fire last");
    }

    /**
     * Removing a building from the lower pile must not affect tribe cards.
     */
    @Test
    void removeCard_RemovesBuildingWithoutAffectingTribes() {
        lowerPile.addCard(basicTribe);
        lowerPile.addCard(basicBuilding);

        lowerPile.removeCard(basicBuilding);

        assertEquals(1, lowerPile.getTribeCardsSize(), "Tribe card must remain untouched");
        assertEquals(List.of(basicTribe), lowerPile.getTribeCards(), "Tribe card should not be affected");
        assertTrue(lowerPile.getBuildingCards().isEmpty(), "Removed building should no longer be present");
    }

    /**
     * Unknown card subtypes are ignored by the lower pile and are not exposed
     * as visible cards.
     */
    @Test
    void addAndRemoveIgnoreUnknownCardSubtype() {
        Card unknownCard = new UnknownCard(99, Era.first);

        lowerPile.addCard(unknownCard);
        lowerPile.removeCard(unknownCard);

        assertTrue(lowerPile.getAllCards().isEmpty());
    }

    /**
     * If no sustenance card is present, resolving lower-row events only applies
     * the regular event cards that are visible.
     */
    @Test
    void resolveLowerRowEvents_WithoutSustenance_ResolvesOnlyRegularEvents() {
        List<String> executionLog = new ArrayList<>();
        ArrayList<Player> dummyPlayers = new ArrayList<>();

        HuntEventCard huntEvent = new HuntEventCard(7, Era.first, TribeCardType.EVENT, 1, 1) {
            @Override
            public void applyEffect(ArrayList<Player> players) {
                executionLog.add("HUNT");
            }
        };

        lowerPile.addCard(huntEvent);
        lowerPile.addCard(basicBuilding);

        lowerPile.resolveLowerRowEvents(dummyPlayers);

        assertEquals(List.of("HUNT"), executionLog, "Without Sustenance only the present event must be resolved");
    }
    /**
     * Multiple sustenance events resolve by era order.
     */
    @Test
    void resolveLowerRowEvents_TwoSustenanceEvents_ResolvesInEraOrder() {
        List<String> executionLog = new ArrayList<>();
        ArrayList<Player> dummyPlayers = new ArrayList<>();

        SustenanceEventCard sustEra3 = new SustenanceEventCard(10, Era.third, TribeCardType.EVENT, 1) {
            @Override
            public void applyEffect(ArrayList<Player> players) { executionLog.add("SUST_ERA3"); }
        };

        SustenanceEventCard sustEra1 = new SustenanceEventCard(11, Era.first, TribeCardType.EVENT, 1) {
            @Override
            public void applyEffect(ArrayList<Player> players) { executionLog.add("SUST_ERA1"); }
        };

        lowerPile.addCard(sustEra3);
        lowerPile.addCard(sustEra1);

        lowerPile.resolveLowerRowEvents(dummyPlayers);

        assertEquals(2, executionLog.size(), "Both events must be resolved");
        assertEquals("SUST_ERA1", executionLog.get(0), "Era 1 Sustenance must fire first");
        assertEquals("SUST_ERA3", executionLog.get(1), "Era 3 Sustenance must fire second");
    }

    /**
     * Multiple regular events of the same type resolve by era order.
     */
    @Test
    void resolveLowerRowEvents_TwoRegularEventsOfSameType_ResolvesInEraOrder() {
        List<String> executionLog = new ArrayList<>();
        ArrayList<Player> dummyPlayers = new ArrayList<>();

        HuntEventCard huntEra2 = new HuntEventCard(12, Era.second, TribeCardType.EVENT, 1, 1) {
            @Override
            public void applyEffect(ArrayList<Player> players) { executionLog.add("HUNT_ERA2"); }
        };

        HuntEventCard huntEra1 = new HuntEventCard(13, Era.first, TribeCardType.EVENT, 1, 1) {
            @Override
            public void applyEffect(ArrayList<Player> players) { executionLog.add("HUNT_ERA1"); }
        };

        lowerPile.addCard(huntEra2);
        lowerPile.addCard(huntEra1);

        lowerPile.resolveLowerRowEvents(dummyPlayers);

        assertEquals(2, executionLog.size(), "Both events must be resolved");
        assertEquals("HUNT_ERA1", executionLog.get(0), "Era 1 Hunt event must fire first");
        assertEquals("HUNT_ERA2", executionLog.get(1), "Era 2 Hunt event must fire second");
    }

    /**
     * Mixed regular and sustenance events first resolve regular events by era,
     * then sustenance events by era.
     */
    @Test
    void resolveLowerRowEvents_MixedEventsAndEras_ResolvesEverythingInCorrectOrder() {
        List<String> executionLog = new ArrayList<>();
        ArrayList<Player> dummyPlayers = new ArrayList<>();

        SustenanceEventCard sustEra3 = new SustenanceEventCard(20, Era.third, TribeCardType.EVENT, 1) {
            @Override
            public void applyEffect(ArrayList<Player> players) { executionLog.add("SUST_ERA3"); }
        };
        HuntEventCard huntEra3 = new HuntEventCard(21, Era.third, TribeCardType.EVENT, 1, 1) {
            @Override
            public void applyEffect(ArrayList<Player> players) { executionLog.add("HUNT_ERA3"); }
        };
        SustenanceEventCard sustEra1 = new SustenanceEventCard(22, Era.first, TribeCardType.EVENT, 1) {
            @Override
            public void applyEffect(ArrayList<Player> players) { executionLog.add("SUST_ERA1"); }
        };
        HuntEventCard huntEra1 = new HuntEventCard(23, Era.first, TribeCardType.EVENT, 1, 1) {
            @Override
            public void applyEffect(ArrayList<Player> players) { executionLog.add("HUNT_ERA1"); }
        };

        lowerPile.addCard(sustEra3);
        lowerPile.addCard(huntEra3);
        lowerPile.addCard(sustEra1);
        lowerPile.addCard(huntEra1);

        lowerPile.resolveLowerRowEvents(dummyPlayers);

        assertEquals(4, executionLog.size());

        assertEquals("HUNT_ERA1", executionLog.get(0));
        assertEquals("HUNT_ERA3", executionLog.get(1));

        assertEquals("SUST_ERA1", executionLog.get(2));
        assertEquals("SUST_ERA3", executionLog.get(3));
    }

    private static final class UnknownCard extends Card {
        private UnknownCard(int id, Era era) {
            super(id, era);
        }

        @Override
        public boolean isDrawable(Player player) {
            return false;
        }
    }
}
