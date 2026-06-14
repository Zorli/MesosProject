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

/**
 * Tests the starting tile as the public next-round queue. The suite verifies
 * setup for supported player counts, insertion order, removal, full-tile
 * behavior, and the visible effects applied when players return.
 */
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

    /**
     * Returning players are placed in the first free blocks, preserving the
     * order in which they come back to the starting tile.
     */
    @Test
    void placePlayerOnNextAvailableBlock_FillsBlocksInOrder() {
        startingTile.removePlayer(player1);
        startingTile.removePlayer(player2);
        startingTile.removePlayer(player3);

        startingTile.placePlayerOnNextAvailableBlock(player1);
        startingTile.placePlayerOnNextAvailableBlock(player2);

        List<Player> order = startingTile.getNextRoundOrder();

        assertEquals(2, order.size(), "There should be exactly 2 players placed");
        assertEquals(player1, order.get(0), "Alice should be on the first available block");
        assertEquals(player2, order.get(1), "Bob should be on the second block");
    }

    /**
     * The next-round order is the order of occupied starting blocks, not the
     * original player list order.
     */
    @Test
    void getNextRoundOrder_ReturnsPlayersCorrectlySorted() {
        startingTile.removePlayer(player1);
        startingTile.removePlayer(player2);
        startingTile.removePlayer(player3);

        startingTile.placePlayerOnNextAvailableBlock(player3);
        startingTile.placePlayerOnNextAvailableBlock(player1);
        startingTile.placePlayerOnNextAvailableBlock(player2);

        List<Player> nextRoundOrder = startingTile.getNextRoundOrder();

        assertEquals(3, nextRoundOrder.size(), "There should be 3 players placed");
        assertEquals(player3, nextRoundOrder.get(0), "Charlie returned first, so he goes first next round");
        assertEquals(player1, nextRoundOrder.get(1), "Alice returned second");
        assertEquals(player2, nextRoundOrder.get(2), "Bob returned last");
    }

    /**
     * Removing a player frees their block and removes them from the next-round
     * order exposed by the tile.
     */
    @Test
    void removePlayer_ClearsTheBlockAndRemovesFromOrder() {
        startingTile.removePlayer(player1);

        List<Player> order = startingTile.getNextRoundOrder();

        assertEquals(2, order.size(), "Only 2 players should remain");
        assertFalse(order.contains(player1), "Alice should no longer be on the Starting Tile");
    }

    /**
     * Placing a player on a full starting tile is ignored, so the current
     * next-round order remains unchanged.
     */
    @Test
    void placePlayerOnNextAvailableBlock_DoesNothingIfTileIsFull() {

        Player hackerPlayer = new Player("Intruso", 0, 0, TotemColor.YELLOW, null);

        startingTile.placePlayerOnNextAvailableBlock(hackerPlayer);

        List<Player> order = startingTile.getNextRoundOrder();
        assertEquals(3, order.size(), "Total slots must remain 3");
        assertFalse(order.contains(hackerPlayer), "The extra player must not have found a slot");
    }

    /**
     * The two-player setup uses the rule-table modifiers and keeps each player
     * in the matching starting block.
     */
    @Test
    void setupForTwoPlayers_UsesExpectedModifiersAndPositions() {
        StartingTile twoPlayerTile = new StartingTile();
        Player alpha = new Player("Alpha", 2, 0, TotemColor.RED, null);
        Player beta = new Player("Beta", 2, 0, TotemColor.BLUE, null);

        twoPlayerTile.setup(List.of(alpha, beta));

        List<StartingBlock> blocks = twoPlayerTile.getStartingBlocks();

        assertEquals(2, blocks.size(), "A 2-player game must have 2 blocks");
        assertEquals(1, blocks.get(0).getPosition());
        assertEquals(1, blocks.get(0).getFoodModifier());
        assertEquals(0, blocks.get(0).getPrestigeModifier());
        assertEquals(alpha, blocks.get(0).getOccupiedBy());
        assertEquals(2, blocks.get(1).getPosition());
        assertEquals(-1, blocks.get(1).getFoodModifier());
        assertEquals(-2, blocks.get(1).getPrestigeModifier());
        assertEquals(beta, blocks.get(1).getOccupiedBy());
    }

    /**
     * Four- and five-player games use different starting tile modifiers. This
     * test checks those public rule tables without relying on implementation
     * details of how the blocks are created.
     */
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

    /**
     * Unsupported player counts do not create a partial starting tile.
     */
    @Test
    void setupWithUnsupportedPlayerCount_LeavesTileEmpty() {
        StartingTile unsupportedTile = new StartingTile();
        unsupportedTile.setup(List.of(new Player("Solo", 2, 0, TotemColor.RED, null)));

        assertTrue(unsupportedTile.getStartingBlocks().isEmpty(), "An unsupported player count must not create any blocks");
    }

    /**
     * Returning to a block immediately applies that block's food or prestige
     * effect to the player.
     */
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

        assertEquals(2, first.getFood(), "First block must grant the expected food bonus");
        assertEquals(0, second.getFood(), "Second block must not deduct food the player does not have");
        assertEquals(3, second.getPrestigePoints(), "Player who cannot pay the food penalty must lose prestige instead");
    }

    /**
     * Placing on the last available block is used for players skipped by
     * disconnection. The player must occupy the last free slot and receive that
     * slot's immediate effect.
     */
    @Test
    void placePlayerOnLastAvailableBlockUsesTheLastFreeSlot() {
        StartingTile twoPlayerTile = new StartingTile();
        Player first = new Player("First", 2, 0, TotemColor.RED, null);
        Player second = new Player("Second", 2, 4, TotemColor.BLUE, null);
        twoPlayerTile.setup(List.of(first, second));
        twoPlayerTile.removePlayer(first);
        twoPlayerTile.removePlayer(second);

        twoPlayerTile.placePlayerOnLastAvailableBlock(second);

        assertEquals(List.of(second), twoPlayerTile.getPlayersOrderedByStartingBlock());
        assertEquals(1, second.getFood());
        assertEquals(4, second.getPrestigePoints());
    }
}
