package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.player.Player;

public class OfferSpace {
    private int positionIndex;
    private String code;
    private int minPlayers;
    private int foodBonus;
    private int topRowCardsToTake;
    private int bottomRowCardsToTake;
    private Player occupiedBy;

    // ==========================================
    // CONSTRUCTOR
    // ==========================================

    public OfferSpace(int positionIndex, String code, int minPlayers, int foodBonus,
                      int topRowCardsToTake, int bottomRowCardsToTake) {
        this.positionIndex = positionIndex;
        this.code = code; // Useful for UI rendering, network messages, or JSON mapping
        this.minPlayers = minPlayers;
        this.foodBonus = foodBonus;
        this.topRowCardsToTake = topRowCardsToTake;
        this.bottomRowCardsToTake = bottomRowCardsToTake;
        this.occupiedBy = null;
    }


    // ==========================================
    // STATE & STATUS CHECKS
    // ==========================================

    /**
     * Determines if this space exists/is playable based on the current player count.
     */
    public boolean isEnabledFor(int playerCount) {
        return playerCount >= this.minPlayers;
    }

    /**
     * Checks if the space is currently free to be occupied.
     */
    public boolean isAvailable() {
        return this.occupiedBy == null;
    }


    // ==========================================
    // PLAYER INTERACTION
    // ==========================================

    /**
     * Places a player's totem on this space.
     */
    public void occupy(Player player) {
        this.occupiedBy = player;
    }

    /**
     * Clears the space, removing the player's totem (used at the end of the round).
     */
    public void clear() {
        this.occupiedBy = null;
    }

    /**
     * Resolves the action when a player is placed here.
     * Contains the immediate food bonus and placeholders for the drafting logic.
     */
    public void resolveAction(Game game, Player player) {
        // 1. Assegna il bonus cibo immediato dello spazio
        if (this.foodBonus > 0) {
            player.gainFood(this.foodBonus); // Assicurati che Player abbia questo metodo!
        }

        // 2. Pesca dalla riga superiore (manca il draftcard da game o da altre parti)
//        for (int i = 0; i < this.topRowCardsToTake; i++) {
//            // game.draftCard restituisce 'true' se il giocatore ha pescato con successo,
//            // o 'false' se non c'erano carte valide o accessibili.
//            boolean success = game.draftCard(player, "UPPER");
//
//            // Se non ha potuto pescare (es. riga vuota o solo carte non acquistabili),
//            // interrompiamo le pescate successive per questa riga.
//            if (!success) {
//                break;
//            }
//        }

        // 3. Pesca dalla riga inferiore
//        for (int i = 0; i < this.bottomRowCardsToTake; i++) {
//            // dopo che il giocatore ha scelta la carta
//            boolean success = game.draftCard(player, "LOWER");
//
//            if (!success) {
//                break;
//            }
//        }
    }


    // ==========================================
    // GETTERS
    // ==========================================

    public Player getOccupiedBy() {
        return this.occupiedBy;
    }

    public int getPositionIndex() {
        return this.positionIndex;
    }

    public String getCode() {
        return this.code;
    }

    public int getMinPlayers() {
        return this.minPlayers;
    }

    public int getTopRowCardsToTake() {
        return this.topRowCardsToTake;
    }

    public int getBottomRowCardsToTake() {
        return this.bottomRowCardsToTake;
    }
}