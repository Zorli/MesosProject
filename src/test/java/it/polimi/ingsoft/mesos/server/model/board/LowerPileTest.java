package it.polimi.ingsoft.mesos.server.model.board;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.cards.events.HuntEventCard;
import it.polimi.ingsoft.mesos.server.model.cards.events.SustenanceEventCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void discardLowerRow_And_discardBuildings_AreIndependent() {
        lowerPile.addCard(basicTribe);
        lowerPile.addCard(basicBuilding);

        // Svuotiamo la riga bassa (le tribù) a fine round
        lowerPile.discardLowerRow();

        assertEquals(0, lowerPile.getTribeCardsSize(), "Le tribù dovrebbero essere state scartate");
        assertEquals(1, lowerPile.getAllCards().size(), "L'edificio deve sopravvivere al fine round");
        assertTrue(lowerPile.getAllCards().contains(basicBuilding), "La carta rimanente deve essere l'edificio");

        // Ora simuliamo il fine Era (scartiamo gli edifici)
        lowerPile.discardLowerBuildings();
        assertEquals(0, lowerPile.getAllCards().size(), "La pila ora deve essere completamente vuota");
    }

    @Test
    void addTribeCards_And_addBuildingCards_BulkAddWorks() {
        List<Gatherer> tribes = List.of(basicTribe, new Gatherer(3, Era.first, TribeCardType.GATHERER));
        List<BuildingCard> buildings = List.of(basicBuilding);

        lowerPile.addTribeCards(new ArrayList<>(tribes));
        lowerPile.addBuildingCards(buildings);

        assertEquals(2, lowerPile.getTribeCardsSize(), "Il bulk add delle tribù deve funzionare");
        assertEquals(3, lowerPile.getAllCards().size(), "Tutte le carte devono essere presenti");
    }

    @Test
    void resolveLowerRowEvents_ExecutesSustenanceLast_RegardlessOfInsertionOrder() {
        // Creiamo un log per tracciare in che ordine "sparano" gli eventi
        List<String> executionLog = new ArrayList<>();
        ArrayList<Player> dummyPlayers = new ArrayList<>();

        // 1. Creiamo un HuntEventCard che scrive "HUNT" nel log quando viene risolto
        HuntEventCard huntEvent = new HuntEventCard(4, Era.first, TribeCardType.EVENT, 1, 1) {
            @Override
            public void applyEffect(ArrayList<Player> players) {
                executionLog.add("HUNT");
            }
        };

        // 2. Creiamo un SustenanceEventCard che scrive "SUSTENANCE" nel log
        // (NOTA: Adatta i parametri del costruttore se il tuo SustenanceEventCard ne richiede di diversi)
        SustenanceEventCard sustenanceEvent = new SustenanceEventCard(5, Era.first, TribeCardType.EVENT, 1) {
            @Override
            public void applyEffect(ArrayList<Player> players) {
                executionLog.add("SUSTENANCE");
            }
        };

        // Inseriamo APPOSITAMENTE prima il Sustenance e poi l'Hunt per testare la forzatura dell'ordine
        lowerPile.addCard(sustenanceEvent);
        lowerPile.addCard(huntEvent);
        lowerPile.addCard(basicTribe); // Mettiamo in mezzo un Gatherer (che non è un evento e verrà ignorato)

        // Risolviamo gli eventi!
        lowerPile.resolveLowerRowEvents(dummyPlayers);

        // Verifiche:
        assertEquals(2, executionLog.size(), "Dovrebbero essersi attivati esattamente 2 eventi");

        // La prova del nove: nonostante il Sustenance sia stato aggiunto per PRIMO, deve aver agito per ULTIMO.
        assertEquals("HUNT", executionLog.get(0), "L'evento normale (Hunt) deve agire per primo");
        assertEquals("SUSTENANCE", executionLog.get(1), "Il Sustenance DEVE agire rigorosamente per ultimo");
    }
}