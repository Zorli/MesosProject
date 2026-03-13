package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.player.Player;

public class StartingBlock {
    private int position;
    private int foodModifier;
    private int prestigeModifier;
    private Player occupiedBy;

    public StartingBlock(int position, int foodModifier, int prestigeModifier) {
        this.position = position;
        this.foodModifier = foodModifier;
        this.prestigeModifier = prestigeModifier;
        this.occupiedBy = null; // Esplicito per chiarezza
    }

    /**
     * Piazzamento di un giocatore sul blocco.
     */
    public void occupy(Player player) {
        this.occupiedBy = player;
    }

    /**
     * Libera il blocco alla fine del round o quando il giocatore si muove.
     */
    public void clear() {
        this.occupiedBy = null;
    }
    public Player getOccupiedBy() {
        return this.occupiedBy;
    }

    public int getFoodModifier() {
        return this.foodModifier;
    }

    public int getPrestigeModifier() {
        return this.prestigeModifier;
    }

    public int getPosition() {
        return this.position;
    }
}
