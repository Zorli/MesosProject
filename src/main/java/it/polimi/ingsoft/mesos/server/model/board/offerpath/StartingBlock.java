package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.player.Player;

/**
 * Represents a single block on the starting tile where players are placed.
 * Each block determines the player's position in the turn order for the upcoming round
 * and applies specific modifiers (food or prestige) immediately upon placement.
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
     * @param foodModifier The amount of food gained (positive) or required to be paid (negative)
     * immediately upon occupying this block.
     * @param prestigeModifier The prestige penalty applied if the player cannot pay a negative food modifier.
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
     * This is typically called when the player moves their totem to an offer space.
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

    /**
     * Resolves the immediate effect of occupying this starting block.
     * Depending on the block's modifiers, the player either gains food, pays a food cost,
     * or suffers a prestige penalty if they cannot afford the required food cost.
     */
    public void resolveEffect() {
        if (this.occupiedBy != null) {
            if (this.foodModifier > 0) {
                this.occupiedBy.gainFood(this.foodModifier);
            } else if (this.foodModifier < 0) {
                int foodCost = Math.abs(this.foodModifier);

                if (this.occupiedBy.getFood() >= foodCost) {
                    this.occupiedBy.payFood(foodCost);
                } else {
                    // Player cannot pay the food cost, apply prestige penalty
                    this.occupiedBy.gainPrestigePoints(this.prestigeModifier);
                }
            }
        }
    }
}