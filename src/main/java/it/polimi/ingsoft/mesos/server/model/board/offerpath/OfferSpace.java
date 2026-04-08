package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.player.Player;

/**
 * Represents a single space on the offer path where a player can place their totem.
 * Each space dictates the drafting rules for the player occupying it and may grant
 * immediate bonuses upon placement.
 */
public class OfferSpace {
    private int positionIndex;
    private String code;
    private int minPlayers;
    private int foodBonus;
    private int topRowCardsToTake;
    private int bottomRowCardsToTake;
    private Player occupiedBy;

    /**
     * Constructs a new offer space with its specific rules and rewards.
     *
     * @param positionIndex The absolute physical position of the space from left to right (e.g., 1, 2, 3).
     * @param code The unique string identifier for the space (e.g., "A", "B").
     * @param minPlayers The minimum number of players required in the game for this space to be active.
     * @param foodBonus The immediate food bonus granted to a player when they occupy this space.
     * @param topRowCardsToTake The number of cards the occupying player can draft from the upper row.
     * @param bottomRowCardsToTake The number of cards the occupying player can draft from the lower row.
     */
    public OfferSpace(int positionIndex, String code, int minPlayers, int foodBonus,
                      int topRowCardsToTake, int bottomRowCardsToTake) {
        this.positionIndex = positionIndex;
        this.code = code;
        this.minPlayers = minPlayers;
        this.foodBonus = foodBonus;
        this.topRowCardsToTake = topRowCardsToTake;
        this.bottomRowCardsToTake = bottomRowCardsToTake;
        this.occupiedBy = null;
    }

    /**
     * Checks if the space is currently unoccupied and available for a player's totem.
     *
     * @return True if the space is free, false if it is already occupied.
     */
    public boolean isAvailable() {
        return this.occupiedBy == null;
    }

    /**
     * Assigns a player to this space, marking it as occupied.
     *
     * @param player The player placing their totem on the space.
     */
    public void occupy(Player player) {
        this.occupiedBy = player;
    }

    /**
     * Clears the space by removing the occupying player.
     * This is triggered the moment a player finishes their active turn
     * and moves their totem back to the starting tile.
     */
    public void clear() {
        this.occupiedBy = null;
    }

    /**
     * Resolves the immediate effects triggered by placing a totem on this space.
     * Currently grants the intrinsic food bonus of the space to the player.
     *
     * @param player The player who occupied the space and receives the effects.
     */
    public void resolveImmediateAction(Player player) {
        if (this.foodBonus > 0) {
            player.gainFood(this.foodBonus);
        }
    }

    /**
     * Retrieves the player currently occupying this space.
     *
     * @return The occupying player, or null if the space is empty.
     */
    public Player getOccupiedBy() {
        return this.occupiedBy;
    }

    /**
     * Retrieves the physical position index of this space on the board.
     * Crucial for determining the new turn order at the end of the round.
     *
     * @return The integer position index.
     */
    public int getPositionIndex() {
        return this.positionIndex;
    }

    /**
     * Retrieves the unique string code identifying this space.
     * Useful for UI rendering, virtualview messages, or API mapping.
     *
     * @return The string code of the space.
     */
    public String getCode() {
        return this.code;
    }

    /**
     * Retrieves the minimum number of players required for this space to exist in the game.
     *
     * @return The minimum player count requirement.
     */
    public int getMinPlayers() {
        return this.minPlayers;
    }

    /**
     * Retrieves the exact number of cards the occupying player is allowed to draft
     * from the upper row.
     *
     * @return The draft amount for the upper row.
     */
    public int getTopRowCardsToTake() {
        return this.topRowCardsToTake;
    }

    /**
     * Retrieves the exact number of cards the occupying player is allowed to draft
     * from the lower row.
     *
     * @return The draft amount for the lower row.
     */
    public int getBottomRowCardsToTake() {
        return this.bottomRowCardsToTake;
    }
}