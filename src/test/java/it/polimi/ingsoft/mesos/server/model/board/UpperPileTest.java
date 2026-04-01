package it.polimi.ingsoft.mesos.server.model.board;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void removeCard_EdgeCase_CardNotInPile_DoesNothing() {
        upperPile.addCard(tribeCard1);

        assertDoesNotThrow(() -> upperPile.removeCard(tribeCard2), "Rimuovere una carta inesistente non deve far crashare il gioco");

        assertEquals(1, upperPile.removeAndReturnAllTribeCards().size(), "La carta presente non deve essere stata alterata");
    }

    @Test
    void removeAndReturnAllCards_EmptiesThePile_AndHandlesEmptyCase() {
        upperPile.addCard(tribeCard1);
        upperPile.addCard(tribeCard2);
        upperPile.addCard(buildingCard1);

        List<?> returnedTribes = upperPile.removeAndReturnAllTribeCards();
        List<BuildingCard> returnedBuildings = upperPile.removeAndReturnAllBuildingCards();

        assertEquals(2, returnedTribes.size(), "Dovrebbe restituire le 2 tribù");
        assertEquals(1, returnedBuildings.size(), "Dovrebbe restituire l'edificio");

        // 2. EDGE CASE: Chiamiamo i metodi su una pila ora VUOTA
        List<?> emptyTribes = upperPile.removeAndReturnAllTribeCards();
        List<BuildingCard> emptyBuildings = upperPile.removeAndReturnAllBuildingCards();

        assertNotNull(emptyTribes, "Non deve mai restituire null, ma una lista vuota");
        assertTrue(emptyTribes.isEmpty(), "La lista delle tribù deve essere vuota");

        assertNotNull(emptyBuildings, "Non deve mai restituire null, ma una lista vuota");
        assertTrue(emptyBuildings.isEmpty(), "La lista degli edifici deve essere vuota");
    }

    @Test
    void restoredConstructorAndGetters_CreateDefensiveCopies() {
        ArrayList<Gatherer> restoredTribes = new ArrayList<>(List.of(tribeCard1, tribeCard2));
        ArrayList<BuildingCard> restoredBuildings = new ArrayList<>(List.of(buildingCard1));

        UpperPile restoredPile = new UpperPile(new ArrayList<>(restoredTribes), new ArrayList<>(restoredBuildings));
        restoredTribes.clear();
        restoredBuildings.clear();

        List<?> visibleTribes = restoredPile.getTribeCards();
        List<BuildingCard> visibleBuildings = restoredPile.getBuildingCards();
        List<?> allCards = restoredPile.getAllCards();

        visibleTribes.clear();
        visibleBuildings.clear();
        allCards.clear();

        assertEquals(2, restoredPile.getTribeCardsSize(), "La pila deve mantenere le tribù ripristinate");
        assertEquals(3, restoredPile.getAllCards().size(), "Le copie restituite non devono alterare lo stato interno");
    }
}
