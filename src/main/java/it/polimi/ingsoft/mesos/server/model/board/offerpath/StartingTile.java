package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.controller.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents the starting tile of the game board, containing the individual starting blocks.
 * Manages the initialization of these blocks based on the player count and handles
 * the end-of-round resolution of food income and prestige penalties based on the turn order.
 */
public class StartingTile {
    private List<StartingBlock> startingBlocks;

    /**
     * Initializes an empty starting tile, ready to be populated with starting blocks
     * during the game setup phase.
     */
    public StartingTile() {
        this.startingBlocks = new ArrayList<>();
    }

    /**
     * Creates a starting tile from already restored starting blocks.
     *
     * @param startingBlocks starting blocks to reuse
     */
    public StartingTile(List<StartingBlock> startingBlocks) {
        this.startingBlocks = new ArrayList<>(startingBlocks);
    }

    /**
     * Configures the starting blocks based on the total number of players in the game.
     * Assigns the specific food and prestige modifiers for each position and places
     * the players on their initial blocks for the very first round.
     *
     * @param players The list of players participating in the game.
     */
    public void setup(List<Player> players) {
        int n = players.size();
        int[] foodModifiers;
        int[] prestigeModifiers;

        if (n == 2) {
            foodModifiers = new int[]{ 1, -1 };
            prestigeModifiers = new int[]{ 0, -2 };
        } else if (n == 3) {
            foodModifiers = new int[]{ 2, 0, -1 };
            prestigeModifiers = new int[]{ 0, 0, -2 };
        } else if (n == 4) {
            foodModifiers = new int[]{ 2, 1, 0, -1 };
            prestigeModifiers = new int[]{ 0, 0, 0, -2 };
        } else if (n == 5) {
            foodModifiers = new int[]{ 3, 1, 0, 0, -1 };
            prestigeModifiers = new int[]{ 0, 0, 0, 0, -2 };
        } else {
            foodModifiers = new int[]{};
            prestigeModifiers = new int[]{};
        }

        for (int i = 0; i < foodModifiers.length; i++) {
            int position = i + 1;
            int food = foodModifiers[i];
            int prestige = prestigeModifiers[i];
            Player startingPlayer = players.get(i);
            this.startingBlocks.add(new StartingBlock(position, food, prestige, startingPlayer));
        }
    }

    /**
     * Places the specified player on the first available starting block.
     * This automatically maintains the return order for the next round and
     * immediately resolves the specific effect of the newly occupied block
     * (e.g., food bonuses or prestige penalties).
     *
     * @param player The player returning to the starting tile.
     */
    public void placePlayerOnNextAvailableBlock(Player player) {
        for (StartingBlock block : this.startingBlocks) {
            if (block.getOccupiedBy() == null) {
                block.occupy(player);
                block.resolveEffect();
                return;
            }
        }
    }

    /**
     * Removes the player from their current starting block.
     * This is triggered when the player moves their totem to an offer space.
     *
     * @param player The player leaving the starting tile.
     */
    public void removePlayer(Player player) {
        this.startingBlocks.stream()
                .filter(block -> player.equals(block.getOccupiedBy()))
                .findFirst()
                .ifPresent(StartingBlock::clear);
    }

    /**
     * Retrieves the list of players currently placed on the starting blocks,
     * in the exact order they will take their turns in the next round.
     *
     * @return A list of players sorted by their turn order for the upcoming round.
     */
    public List<Player> getNextRoundOrder() {
        return this.startingBlocks.stream()
                .filter(block -> block.getOccupiedBy() != null)
                .map(StartingBlock::getOccupiedBy)
                .collect(Collectors.toList());
    }

    /**
     * Returns the current starting blocks preserving their order.
     *
     * @return copy of current starting blocks
     */
    public List<StartingBlock> getStartingBlocks() {
        return new ArrayList<>(this.startingBlocks);
    }
}
