package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.game.board.offerpath.StartingBlock;
import it.polimi.ingsoft.mesos.server.model.game.board.offerpath.StartingTile;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartingTileTest {

    private StartingTile startingTile;
    private Player player1;
    private Player player2;
    private Player player3;

    @BeforeEach
    void setUp() {
        startingTile = new StartingTile();

        player1 = new Player("Alice", 2, 0, TotemColor.RED, null);
        player2 = new Player("Bob", 2, 0, TotemColor.BLUE, null);
        player3 = new Player("Charlie", 2, 0, TotemColor.BLACK, null);

        startingTile.setup(List.of(player1, player2, player3));
    }

    @Test
    void placePlayerOnNextAvailableBlock_FillsBlocksInOrder() {
        startingTile.removePlayer(player1);
        startingTile.removePlayer(player2);
        startingTile.removePlayer(player3);

        startingTile.placePlayerOnNextAvailableBlock(player1);
        startingTile.placePlayerOnNextAvailableBlock(player2);

        List<Player> order = startingTile.getNextRoundOrder();

        assertEquals(2, order.size(), "Dovrebbero esserci esattamente 2 giocatori piazzati");
        assertEquals(player1, order.get(0), "Alice dovrebbe essere sul primo blocco disponibile");
        assertEquals(player2, order.get(1), "Bob dovrebbe essere sul secondo blocco");
    }

    @Test
    void getNextRoundOrder_ReturnsPlayersCorrectlySorted() {
        startingTile.removePlayer(player1);
        startingTile.removePlayer(player2);
        startingTile.removePlayer(player3);

        startingTile.placePlayerOnNextAvailableBlock(player3); // Charlie va sul blocco 1
        startingTile.placePlayerOnNextAvailableBlock(player1); // Alice va sul blocco 2
        startingTile.placePlayerOnNextAvailableBlock(player2); // Bob va sul blocco 3

        List<Player> nextRoundOrder = startingTile.getNextRoundOrder();

        assertEquals(3, nextRoundOrder.size(), "Dovrebbero esserci 3 giocatori piazzati");
        assertEquals(player3, nextRoundOrder.get(0), "Charlie è tornato per primo, sarà il primo di turno");
        assertEquals(player1, nextRoundOrder.get(1), "Alice è tornata per seconda");
        assertEquals(player2, nextRoundOrder.get(2), "Bob è tornato per ultimo");
    }
    @Test
    void removePlayer_ClearsTheBlockAndRemovesFromOrder() {
        startingTile.removePlayer(player1);

        List<Player> order = startingTile.getNextRoundOrder();

        assertEquals(2, order.size(), "Dovrebbero rimanere solo 2 giocatori");
        assertFalse(order.contains(player1), "Alice non dovrebbe più essere sulla Starting Tile");
    }

    @Test
    void placePlayerOnNextAvailableBlock_DoesNothingIfTileIsFull() {

        Player hackerPlayer = new Player("Intruso", 0, 0, TotemColor.YELLOW, null);

        startingTile.placePlayerOnNextAvailableBlock(hackerPlayer);

        List<Player> order = startingTile.getNextRoundOrder();
        assertEquals(3, order.size(), "I posti totali devono rimanere 3");
        assertFalse(order.contains(hackerPlayer), "L'intruso non deve aver trovato posto");
    }

    @Test
    void setupForTwoPlayers_UsesExpectedModifiersAndPositions() {
        StartingTile twoPlayerTile = new StartingTile();
        Player alpha = new Player("Alpha", 2, 0, TotemColor.RED, null);
        Player beta = new Player("Beta", 2, 0, TotemColor.BLUE, null);

        twoPlayerTile.setup(List.of(alpha, beta));

        List<StartingBlock> blocks = twoPlayerTile.getStartingBlocks();

        assertEquals(2, blocks.size(), "Con 2 giocatori devono esserci 2 blocchi");
        assertEquals(1, blocks.get(0).getPosition());
        assertEquals(1, blocks.get(0).getFoodModifier());
        assertEquals(0, blocks.get(0).getPrestigeModifier());
        assertEquals(alpha, blocks.get(0).getOccupiedBy());
        assertEquals(2, blocks.get(1).getPosition());
        assertEquals(-1, blocks.get(1).getFoodModifier());
        assertEquals(-2, blocks.get(1).getPrestigeModifier());
        assertEquals(beta, blocks.get(1).getOccupiedBy());
    }

    @Test
    void setupForFourAndFivePlayers_UsesCorrectRuleTables() {
        StartingTile fourPlayerTile = new StartingTile();
        List<Player> fourPlayers = List.of(
                new Player("A", 2, 0, TotemColor.RED, null),
                new Player("B", 2, 0, TotemColor.BLUE, null),
                new Player("C", 2, 0, TotemColor.BLACK, null),
                new Player("D", 2, 0, TotemColor.YELLOW, null)
        );
        fourPlayerTile.setup(fourPlayers);

        List<StartingBlock> fourPlayerBlocks = fourPlayerTile.getStartingBlocks();
        assertEquals(List.of(2, 1, 0, -1), fourPlayerBlocks.stream().map(StartingBlock::getFoodModifier).toList());
        assertEquals(List.of(0, 0, 0, -2), fourPlayerBlocks.stream().map(StartingBlock::getPrestigeModifier).toList());

        StartingTile fivePlayerTile = new StartingTile();
        List<Player> fivePlayers = List.of(
                new Player("E", 2, 0, TotemColor.RED, null),
                new Player("F", 2, 0, TotemColor.BLUE, null),
                new Player("G", 2, 0, TotemColor.BLACK, null),
                new Player("H", 2, 0, TotemColor.YELLOW, null),
                new Player("I", 2, 0, TotemColor.WHITE, null)
        );
        fivePlayerTile.setup(fivePlayers);

        List<StartingBlock> fivePlayerBlocks = fivePlayerTile.getStartingBlocks();
        assertEquals(List.of(3, 1, 0, 0, -1), fivePlayerBlocks.stream().map(StartingBlock::getFoodModifier).toList());
        assertEquals(List.of(0, 0, 0, 0, -2), fivePlayerBlocks.stream().map(StartingBlock::getPrestigeModifier).toList());
    }

    @Test
    void setupWithUnsupportedPlayerCount_LeavesTileEmpty() {
        StartingTile unsupportedTile = new StartingTile();
        unsupportedTile.setup(List.of(new Player("Solo", 2, 0, TotemColor.RED, null)));

        assertTrue(unsupportedTile.getStartingBlocks().isEmpty(), "Con un numero non supportato di giocatori non vanno creati blocchi");
    }

    @Test
    void placePlayerOnNextAvailableBlock_AppliesBlockEffectWhenReturning() {
        StartingTile twoPlayerTile = new StartingTile();
        Player first = new Player("First", 1, 0, TotemColor.RED, null);
        Player second = new Player("Second", 0, 5, TotemColor.BLUE, null);
        twoPlayerTile.setup(List.of(first, second));

        twoPlayerTile.removePlayer(first);
        twoPlayerTile.removePlayer(second);

        twoPlayerTile.placePlayerOnNextAvailableBlock(first);
        twoPlayerTile.placePlayerOnNextAvailableBlock(second);

        assertEquals(2, first.getFood(), "Il primo blocco deve assegnare il bonus cibo previsto");
        assertEquals(0, second.getFood(), "Il secondo blocco non deve pagare cibo che il giocatore non possiede");
        assertEquals(3, second.getPrestigePoints(), "Se il giocatore non paga il malus cibo deve perdere prestigio");
    }
}
