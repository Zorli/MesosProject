package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.game.board.offerpath.OfferPath;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the offer path as the public placement area used during totem
 * placement. The suite checks only observable effects: occupied spaces,
 * returned draft actions, event A resolution, and next-round ordering.
 */
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

    /**
     * Placing a player on an offer space must reserve that space and remove the
     * player from the starting tile order.
     */
    @Test
    void placePlayer_MovesPlayerFromStartingTileToOfferSpace() {
        offerPath.placePlayer(player1, "B");

        assertFalse(offerPath.checkOfferSpace("B"), "Lo spazio B non dovrebbe più essere disponibile");
        assertFalse(offerPath.getNextRoundTurnOrder().contains(player1), "Alice non dovrebbe più essere sulla Starting Tile");
    }

    /**
     * Each offer space exposes the number of upper and lower cards the player
     * may draft. A player who is not placed on the path has no draft action.
     */
    @Test
    void resolveActionForPlayer_ReturnsCorrectArray_AndEdgeCaseFallback() {
        offerPath.placePlayer(player2, "C");

        int[] action = offerPath.resolveActionForPlayer(player2);
        assertEquals(1, action[0], "Dovrebbe pescare 1 da sopra");
        assertEquals(0, action[1], "Dovrebbe pescare 0 da sotto");

        int[] fallbackAction = offerPath.resolveActionForPlayer(player4);
        assertArrayEquals(new int[]{0, 0}, fallbackAction, "Un giocatore non piazzato dovrebbe ricevere [0, 0]");
    }

    /**
     * Event space A gives its food bonus only to the player placed there, then
     * frees the space and returns that player to the starting tile.
     */
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

    /**
     * Returning a player from the offer path must make their previous space
     * available again and include the player in the next-round order.
     */
    @Test
    void returnPlayerToStartingTile_ClearsSpaceAndReturnsToStart() {
        offerPath.placePlayer(player3, "D");

        offerPath.returnPlayerToStartingTile(player3);

        assertTrue(offerPath.checkOfferSpace("D"), "Lo spazio D deve essere tornato disponibile");
        assertTrue(offerPath.getNextRoundTurnOrder().contains(player3), "Charlie deve essere tornato sulla Starting Tile");
    }

    /**
     * Draft resolution order follows offer-space order and intentionally skips
     * space A, because A is resolved by its event instead of card drafting.
     */
    @Test
    void getPlayersInOrder_SortsCorrectly_AndIgnoresSpaceA() {
        offerPath.placePlayer(player3, "D");
        offerPath.placePlayer(player1, "A");
        offerPath.placePlayer(player2, "C");

        List<Player> resolutionOrder = offerPath.getPlayersInOrder();

        assertEquals(2, resolutionOrder.size(), "Dovrebbero esserci 2 giocatori (Spazio A ignorato)");

        assertEquals(player2, resolutionOrder.get(0), "Bob su C dovrebbe agire per primo");
        assertEquals(player3, resolutionOrder.get(1), "Charlie su D dovrebbe agire per secondo");
    }

    /**
     * A player can be moved to the last available starting block from either
     * the offer path or the starting tile. This is the public behavior used when
     * disconnected players must be parked without keeping an offer space busy.
     */
    @Test
    void movePlayerToLastStartingTileFreesThePreviousPosition() {
        offerPath.placePlayer(player1, "B");

        offerPath.movePlayerToLastStartingTile(player1, 0);

        assertTrue(offerPath.checkOfferSpace("B"));
        assertTrue(offerPath.getNextRoundTurnOrder().contains(player1));

        offerPath.movePlayerToLastStartingTile(player2, 1);

        assertTrue(offerPath.getNextRoundTurnOrder().contains(player2));
    }

    /**
     * Available spaces are computed from the current occupancy and are the
     * values the UI can offer to a player for totem placement.
     */
    @Test
    void getAvailableOfferSpacesReturnsOnlyFreeSpaces() {
        offerPath.placePlayer(player1, "B");
        offerPath.placePlayer(player2, "C");

        List<String> availableCodes = offerPath.getAvailableOfferSpaces().stream()
                .map(space -> space.getCode())
                .toList();

        assertFalse(availableCodes.contains("B"));
        assertFalse(availableCodes.contains("C"));
        assertTrue(availableCodes.contains("A"));
        assertTrue(availableCodes.contains("G"));
    }
}
