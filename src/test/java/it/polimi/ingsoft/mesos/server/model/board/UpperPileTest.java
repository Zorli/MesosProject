package it.polimi.ingsoft.mesos.server.model.board;

import it.polimi.ingsoft.mesos.server.model.game.board.UpperPile;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.Card;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the upper pile as the visible container for upper-row cards. The suite
 * checks observable routing between tribe and building cards, safe removal,
 * extraction, and copy-based getters.
 */
class UpperPileTest {

    private UpperPile upperPile;
    private Gatherer tribeCard1;
    private Gatherer tribeCard2;
    private BuildingCard buildingCard1;

    @BeforeEach
    void setUp() {
        upperPile = new UpperPile();

        tribeCard1 = new Gatherer(1, Era.first, TribeCardType.GATHERER);
        tribeCard2 = new Gatherer(2, Era.first, TribeCardType.GATHERER);
        buildingCard1 = new BuildingCard(3, Era.first, 2, 1, BenefitType.END_ROUND_EXTRA_CARD);
    }

    /**
     * Adding mixed card types must route tribe cards and building cards to the
     * correct visible collections, and removing one card must leave the other
     * card type untouched.
     */
    @Test
    void addCard_And_removeCard_RouteToCorrectLists() {
        upperPile.addCard(tribeCard1);
        upperPile.addCard(buildingCard1);

        upperPile.removeCard(tribeCard1);

        List<BuildingCard> remainingBuildings = upperPile.removeAndReturnAllBuildingCards();
        List<Gatherer> remainingTribes = (List<Gatherer>)(List<?>) upperPile.removeAndReturnAllTribeCards();

        assertEquals(1, remainingBuildings.size(), "L'edificio dovrebbe essere ancora al suo posto");
        assertEquals(buildingCard1, remainingBuildings.get(0));
        assertTrue(remainingTribes.isEmpty(), "La tribù dovrebbe essere stata rimossa correttamente");
    }

    /**
     * Removing a card that is not present is a safe no-op and must not alter
     * the cards already visible in the pile.
     */
    @Test
    void removeCard_EdgeCase_CardNotInPile_DoesNothing() {
        upperPile.addCard(tribeCard1);

        assertDoesNotThrow(() -> upperPile.removeCard(tribeCard2), "Rimuovere una carta inesistente non deve far crashare il gioco");

        assertEquals(1, upperPile.removeAndReturnAllTribeCards().size(), "La carta presente non deve essere stata alterata");
    }

    /**
     * Unknown card subtypes are not exposed as draftable upper-pile cards.
     */
    @Test
    void addAndRemoveIgnoreUnknownCardSubtype() {
        Card unknownCard = new UnknownCard(99, Era.first);

        upperPile.addCard(unknownCard);
        upperPile.removeCard(unknownCard);

        assertTrue(upperPile.getAllCards().isEmpty());
    }

    /**
     * Extracting all cards consumes the pile. Calling the same extraction again
     * returns empty lists instead of null.
     */
    @Test
    void removeAndReturnAllCards_EmptiesThePile_AndHandlesEmptyCase() {
        upperPile.addCard(tribeCard1);
        upperPile.addCard(tribeCard2);
        upperPile.addCard(buildingCard1);

        List<?> returnedTribes = upperPile.removeAndReturnAllTribeCards();
        List<BuildingCard> returnedBuildings = upperPile.removeAndReturnAllBuildingCards();

        assertEquals(2, returnedTribes.size(), "Dovrebbe restituire le 2 tribù");
        assertEquals(1, returnedBuildings.size(), "Dovrebbe restituire l'edificio");

        List<?> emptyTribes = upperPile.removeAndReturnAllTribeCards();
        List<BuildingCard> emptyBuildings = upperPile.removeAndReturnAllBuildingCards();

        assertNotNull(emptyTribes, "Non deve mai restituire null, ma una lista vuota");
        assertTrue(emptyTribes.isEmpty(), "La lista delle tribù deve essere vuota");

        assertNotNull(emptyBuildings, "Non deve mai restituire null, ma una lista vuota");
        assertTrue(emptyBuildings.isEmpty(), "La lista degli edifici deve essere vuota");
    }

    /**
     * The visible-card getters expose copies of the upper-row cards without
     * consuming them. They are used by board payload mapping and draft checks.
     */
    @Test
    void visibleCardGettersReturnCopiesWithoutRemovingCards() {
        upperPile.addCard(tribeCard1);
        upperPile.addCard(buildingCard1);

        var tribeCards = upperPile.getTribeCards();
        List<BuildingCard> buildingCards = upperPile.getBuildingCards();

        assertEquals(List.of(tribeCard1), tribeCards);
        assertEquals(List.of(buildingCard1), buildingCards);
        tribeCards.clear();
        buildingCards.clear();

        assertEquals(2, upperPile.getAllCards().size());
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
