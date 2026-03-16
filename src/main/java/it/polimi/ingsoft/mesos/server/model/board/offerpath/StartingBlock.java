package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.player.Player;

/**
 * Represents a single block on the starting tile where players are placed
 * at the beginning of a round. Each block determines the player's position
 * in the turn order and may apply specific modifiers (food or prestige)
 * at the end of the round.
 */
public class StartingBlock {
    private int position;
    private int foodModifier;
    private int prestigeModifier;
    private Player occupiedBy;

    /**
     * Initializes a new starting block with its designated position and modifiers.
     *
     * @param position The turn order position of this block (e.g., 1 for first player).
     * @param foodModifier The amount of food gained (positive) or lost (negative)
     * by the player occupying this block at the end of the round.
     * @param prestigeModifier The amount of prestige gained (positive) or lost (negative)
     * by the player occupying this block at the end of the round.
     * @param occupiedBy The player initially assigned to this block (usually null at creation).
     */
    public StartingBlock(int position, int foodModifier, int prestigeModifier, Player occupiedBy) {
        this.position = position;
        this.foodModifier = foodModifier;
        this.prestigeModifier = prestigeModifier;
        this.occupiedBy = occupiedBy;
    }

    /**
     * Assigns a player's totem to this starting block, establishing their
     * place in the turn order for the upcoming round.
     *
     * @param player The player to be placed on this block.
     */
    public void occupy(Player player) {
        this.occupiedBy = player;
    }

    /**
     * Clears the block by removing the occupying player.
     * This is typically called when the player moves their totem to an offer space
     * or during the board reset phase.
     */
    public void clear() {
        this.occupiedBy = null;
    }

    /**
     * Retrieves the player currently occupying this starting block.
     *
     * @return The occupying player, or null if the block is empty.
     */
    public Player getOccupiedBy() {
        return this.occupiedBy;
    }

    /**
     * Retrieves the food modifier associated with this starting block.
     *
     * @return The integer value of the food modifier.
     */
    public int getFoodModifier() {
        return this.foodModifier;
    }

    /**
     * Retrieves the prestige modifier associated with this starting block.
     *
     * @return The integer value of the prestige modifier.
     */
    public int getPrestigeModifier() {
        return this.prestigeModifier;
    }
}