package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.player.Player;

public class StartingBlock {
    private int position;
    private int foodModifier;
    private int prestigeModifier;
    private Player occupiedBy;

    // ==========================================
    // CONSTRUCTOR
    // ==========================================

    public StartingBlock(int position, int foodModifier, int prestigeModifier, Player occupiedBy) {
        this.position = position;
        this.foodModifier = foodModifier;
        this.prestigeModifier = prestigeModifier;
        this.occupiedBy = occupiedBy;
    }


    // ==========================================
    // PLAYER INTERACTION
    // ==========================================

    /**
     * Places a player's totem on this starting block.
     */
    public void occupy(Player player) {
        this.occupiedBy = player;
    }

    /**
     * Clears the block at the end of the round or when the player moves.
     */
    public void clear() {
        this.occupiedBy = null;
    }


    // ==========================================
    // GETTERS
    // ==========================================

    public Player getOccupiedBy() {
        return this.occupiedBy;
    }

    public int getFoodModifier() {
        return this.foodModifier;
    }

    public int getPrestigeModifier() {
        return this.prestigeModifier;
    }
}