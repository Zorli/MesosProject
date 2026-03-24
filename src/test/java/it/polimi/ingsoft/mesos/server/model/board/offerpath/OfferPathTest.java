package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.controller.player.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OfferPathTest {

    private OfferPath offerPath;
    private Player player1, player2, player3, player4, player5;

    @BeforeEach
    void setUp() {
        offerPath = new OfferPath();

        // Creiamo 5 giocatori per sbloccare tutti gli spazi, incluso "A" (che ha minPlayers = 5)
        player1 = new Player("Alice", 2, 0, TotemColor.RED, null);
        player2 = new Player("Bob", 2, 0, TotemColor.BLUE, null);
        player3 = new Player("Charlie", 2, 0, TotemColor.WHITE, null);
        player4 = new Player("Dave", 2, 0, TotemColor.YELLOW, null);
        player5 = new Player("Eve", 2, 0, TotemColor.BLACK, null);

        offerPath.setup(List.of(player1, player2, player3, player4, player5));
    }

    @Test
    void placePlayer_MovesPlayerFromStartingTileToOfferSpace() {
        // All'inizio, Alice è sulla Starting Tile. La spostiamo sullo spazio B.
        offerPath.placePlayer(player1, "B");

        assertFalse(offerPath.checkOfferSpace("B"), "Lo spazio B non dovrebbe più essere disponibile");
        assertFalse(offerPath.getNextRoundTurnOrder().contains(player1), "Alice non dovrebbe più essere sulla Starting Tile");
    }

    @Test
    void resolveActionForPlayer_ReturnsCorrectArray_AndEdgeCaseFallback() {
        offerPath.placePlayer(player2, "C"); // Lo spazio C dà [1, 0] (1 top, 0 bottom)

        int[] action = offerPath.resolveActionForPlayer(player2);
        assertEquals(1, action[0], "Dovrebbe pescare 1 da sopra");
        assertEquals(0, action[1], "Dovrebbe pescare 0 da sotto");

        // EDGE CASE: Giocatore non sulla plancia (es. Dave non è ancora stato piazzato)
        int[] fallbackAction = offerPath.resolveActionForPlayer(player4);
        assertArrayEquals(new int[]{0, 0}, fallbackAction, "Un giocatore non piazzato dovrebbe ricevere [0, 0]");
    }

    @Test
    void resolveEventA_GrantsBonusAndReturnsPlayerToStart() {
        // EDGE CASE: Spazio A vuoto. Verifichiamo che non crashi.
        offerPath.resolveEventA();
        assertEquals(2, player1.getFood(), "Nessuno è su A, il cibo non deve cambiare");

        // Piazziamo Alice su A e verifichiamo che abbia lasciato la Starting Tile
        offerPath.placePlayer(player1, "A");
        assertFalse(offerPath.getNextRoundTurnOrder().contains(player1));

        // Risolviamo l'evento! (dà +3 cibo e la rimanda a casa)
        offerPath.resolveEventA();

        // Verifiche:
        // 1. Cibo aumentato, sia per lo spazio A che per il ritorno
        assertEquals(8, player1.getFood(), "Alice era su A, dovrebbe aver ricevuto 3 cibo extra");
        // 2. Spazio A svuotato?
        assertTrue(offerPath.checkOfferSpace("A"), "Lo spazio A dovrebbe essere tornato disponibile dopo la risoluzione");
        // 3. Alice tornata alla base?
        assertTrue(offerPath.getNextRoundTurnOrder().contains(player1), "Alice deve essere stata rimessa sulla Starting Tile");
    }

    @Test
    void returnPlayerToStartingTile_ClearsSpaceAndReturnsToStart() {
        offerPath.placePlayer(player3, "D");

        // Charlie finisce il turno e torna indietro manualmente
        offerPath.returnPlayerToStartingTile(player3);

        assertTrue(offerPath.checkOfferSpace("D"), "Lo spazio D deve essere tornato disponibile");
        assertTrue(offerPath.getNextRoundTurnOrder().contains(player3), "Charlie deve essere tornato sulla Starting Tile");
    }

    @Test
    void getPlayersInOrder_SortsCorrectly_AndIgnoresSpaceA() {
        // Piazziamo i giocatori in disordine
        offerPath.placePlayer(player3, "D"); // Charlie (Pos 4)
        offerPath.placePlayer(player1, "A"); // Alice (Pos 1 - MA DEVE ESSERE IGNORATA)
        offerPath.placePlayer(player2, "C"); // Bob (Pos 3)

        List<Player> resolutionOrder = offerPath.getPlayersInOrder();

        // Dovrebbero esserci solo 2 giocatori (Spazio A ignorato dal filtro)
        assertEquals(2, resolutionOrder.size(), "Dovrebbero esserci 2 giocatori (Spazio A ignorato)");

        // L'ordine corretto da sinistra a destra (escluso A) è: Spazio C (Bob), Spazio D (Charlie)
        assertEquals(player2, resolutionOrder.get(0), "Bob su C dovrebbe agire per primo");
        assertEquals(player3, resolutionOrder.get(1), "Charlie su D dovrebbe agire per secondo");
    }
}