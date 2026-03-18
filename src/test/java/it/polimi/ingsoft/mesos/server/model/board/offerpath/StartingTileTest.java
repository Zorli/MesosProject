package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StartingTileTest {

    private StartingTile startingTile;
    private Player player1;
    private Player player2;
    private Player player3;

    @BeforeEach
    void setUp() {
        // Creiamo la tile vuota
        startingTile = new StartingTile();

        // Creiamo tre giocatori fittizi
        player1 = new Player("Alice", 2, 0, TotemColor.RED, null);
        player2 = new Player("Bob", 2, 0, TotemColor.BLUE, null);
        player3 = new Player("Charlie", 2, 0, TotemColor.BLACK, null);

        // Facciamo il setup della StartingTile per 3 giocatori
        startingTile.setup(List.of(player1, player2, player3));
    }

    @Test
    void placePlayerOnNextAvailableBlock_FillsBlocksInOrder() {
        // 1. Svuotiamo la tile simulando che i giocatori siano andati sulle offerte
        startingTile.removePlayer(player1);
        startingTile.removePlayer(player2);
        startingTile.removePlayer(player3);

        // 2. Ora la tile è vuota. Facciamo "tornare" solo due giocatori:
        startingTile.placePlayerOnNextAvailableBlock(player1);
        startingTile.placePlayerOnNextAvailableBlock(player2);

        // 3. Verifichiamo l'ordine
        List<Player> order = startingTile.getNextRoundOrder();

        assertEquals(2, order.size(), "Dovrebbero esserci esattamente 2 giocatori piazzati");
        assertEquals(player1, order.get(0), "Alice dovrebbe essere sul primo blocco disponibile");
        assertEquals(player2, order.get(1), "Bob dovrebbe essere sul secondo blocco");
    }

    @Test
    void getNextRoundOrder_ReturnsPlayersCorrectlySorted() {
        // 1. Svuotiamo la tile
        startingTile.removePlayer(player1);
        startingTile.removePlayer(player2);
        startingTile.removePlayer(player3);

        // 2. Li facciamo tornare in un ordine diverso (es. Charlie finisce per primo il turno)
        startingTile.placePlayerOnNextAvailableBlock(player3); // Charlie va sul blocco 1
        startingTile.placePlayerOnNextAvailableBlock(player1); // Alice va sul blocco 2
        startingTile.placePlayerOnNextAvailableBlock(player2); // Bob va sul blocco 3

        // 3. Estraiamo l'ordine per il prossimo round
        List<Player> nextRoundOrder = startingTile.getNextRoundOrder();

        // Il nuovo ordine dei turni dovrà essere esattamente quello di arrivo
        assertEquals(3, nextRoundOrder.size(), "Dovrebbero esserci 3 giocatori piazzati");
        assertEquals(player3, nextRoundOrder.get(0), "Charlie è tornato per primo, sarà il primo di turno");
        assertEquals(player1, nextRoundOrder.get(1), "Alice è tornata per seconda");
        assertEquals(player2, nextRoundOrder.get(2), "Bob è tornato per ultimo");
    }
    @Test
    void removePlayer_ClearsTheBlockAndRemovesFromOrder() {
        // Dal setup, Alice (player1) è sicuramente sulla Starting Tile.
        // Proviamo a rimuoverla.
        startingTile.removePlayer(player1);

        List<Player> order = startingTile.getNextRoundOrder();

        // Verifichiamo che Alice sia effettivamente sparita e gli altri due siano rimasti
        assertEquals(2, order.size(), "Dovrebbero rimanere solo 2 giocatori");
        assertFalse(order.contains(player1), "Alice non dovrebbe più essere sulla Starting Tile");
    }

    @Test
    void placePlayerOnNextAvailableBlock_DoesNothingIfTileIsFull() {
        // Dal setup la tile ha 3 blocchi e sono già tutti occupati da Alice, Bob e Charlie.
        // Creiamo un intruso!
        Player hackerPlayer = new Player("Intruso", 0, 0, TotemColor.YELLOW, null);

        // Proviamo a piazzarlo a forza su una tile già piena
        startingTile.placePlayerOnNextAvailableBlock(hackerPlayer);

        List<Player> order = startingTile.getNextRoundOrder();

        // Verifichiamo che il codice sia "safe" e non sia crashato,
        // e che l'intruso non sia stato aggiunto misteriosamente.
        assertEquals(3, order.size(), "I posti totali devono rimanere 3");
        assertFalse(order.contains(hackerPlayer), "L'intruso non deve aver trovato posto");
    }
}