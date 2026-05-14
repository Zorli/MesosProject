package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.game.board.offerpath.OfferPath;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
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

        player1 = new Player("Alice", 2, 0, TotemColor.RED, null);
        player2 = new Player("Bob", 2, 0, TotemColor.BLUE, null);
        player3 = new Player("Charlie", 2, 0, TotemColor.WHITE, null);
        player4 = new Player("Dave", 2, 0, TotemColor.YELLOW, null);
        player5 = new Player("Eve", 2, 0, TotemColor.BLACK, null);

        offerPath.setup(List.of(player1, player2, player3, player4, player5));
    }

    @Test
    void placePlayer_MovesPlayerFromStartingTileToOfferSpace() {
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

        int[] fallbackAction = offerPath.resolveActionForPlayer(player4);
        assertArrayEquals(new int[]{0, 0}, fallbackAction, "Un giocatore non piazzato dovrebbe ricevere [0, 0]");
    }

    @Test
    void resolveEventA_GrantsBonusAndReturnsPlayerToStart() {
        offerPath.resolveEventA();
        assertEquals(2, player1.getFood(), "Nessuno è su A, il cibo non deve cambiare");

        offerPath.placePlayer(player1, "A");
        assertFalse(offerPath.getNextRoundTurnOrder().contains(player1));

        offerPath.resolveEventA();

        assertEquals(8, player1.getFood(), "Alice era su A, dovrebbe aver ricevuto 3 cibo extra");
        assertTrue(offerPath.checkOfferSpace("A"), "Lo spazio A dovrebbe essere tornato disponibile dopo la risoluzione");
        assertTrue(offerPath.getNextRoundTurnOrder().contains(player1), "Alice deve essere stata rimessa sulla Starting Tile");
    }

    @Test
    void returnPlayerToStartingTile_ClearsSpaceAndReturnsToStart() {
        offerPath.placePlayer(player3, "D");

        offerPath.returnPlayerToStartingTile(player3);

        assertTrue(offerPath.checkOfferSpace("D"), "Lo spazio D deve essere tornato disponibile");
        assertTrue(offerPath.getNextRoundTurnOrder().contains(player3), "Charlie deve essere tornato sulla Starting Tile");
    }

    @Test
    void getPlayersInOrder_SortsCorrectly_AndIgnoresSpaceA() {
        offerPath.placePlayer(player3, "D"); // Charlie (Pos 4)
        offerPath.placePlayer(player1, "A"); // Alice (Pos 1 - MA DEVE ESSERE IGNORATA)
        offerPath.placePlayer(player2, "C"); // Bob (Pos 3)

        List<Player> resolutionOrder = offerPath.getPlayersInOrder();

        assertEquals(2, resolutionOrder.size(), "Dovrebbero esserci 2 giocatori (Spazio A ignorato)");

        assertEquals(player2, resolutionOrder.get(0), "Bob su C dovrebbe agire per primo");
        assertEquals(player3, resolutionOrder.get(1), "Charlie su D dovrebbe agire per secondo");
    }
}