package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class OfferPath {
    private StartingTile startingTile;
    private List<OfferSpace> offerSpaces;

    // ==========================================
    // CONSTRUCTOR
    // ==========================================

    public OfferPath() {
        this.startingTile = new StartingTile();
        this.offerSpaces = new ArrayList<>();
    }


    // ==========================================
    // INITIAL SETUP
    // ==========================================

    public void setup(List<Player> players) {
        int n = players.size();
        this.setupOfferSpaces(n);
        this.startingTile.setup(players);
    }

    private void setupOfferSpaces(int numPlayer) {
        // Dati di configurazione hardcodati (ideale spostarli in futuro su un JSON)
        List<OfferSpace> allPossibleSpaces = List.of(
                // Position, Code, MinPlayers, FoodBonus, TopRow, BottomRow
                new OfferSpace(1, "A", 5, 3, 0, 0),
                new OfferSpace(2, "B", 2, 0, 0, 1),
                new OfferSpace(3, "C", 2, 0, 1, 0),
                new OfferSpace(4, "D", 3, 0, 0, 2),
                new OfferSpace(5, "E", 2, 0, 1, 1),
                new OfferSpace(6, "F", 2, 0, 2, 0),
                new OfferSpace(7, "G", 4, 0, 2, 1)
        );

        // Filtra e aggiunge solo gli spazi validi per il numero di giocatori
        for (OfferSpace space : allPossibleSpaces) {
            if (space.getMinPlayers() <= numPlayer) {
                this.offerSpaces.add(space);
            }
        }
    }


    // ==========================================
    // END OF ROUND LOGIC
    // ==========================================

    /**
     * Called at the end of the round. Extracts the new turn order,
     * clears the offer spaces, and resets the players on the starting tile.
     * * @return The list of players in their new turn order.
     */
    public List<Player> resetAndGetNewOrder() {
        // 1. Extract the new order based on current totem positions
        List<Player> newOrder = this.getPlayersInOrder();

        // 2. Clear all offer spaces (set occupiedBy to null)
        this.clearOfferSpaces();

        // 3. Pass the new order to the Starting Tile for placement and effect resolution
        this.startingTile.placePlayersAndResolveEffects(newOrder);

        // 4. Return the new order to the Board (and consequently to TurnOrderTrack)
        return newOrder;
    }


    // ==========================================
    // HELPER METHODS
    // ==========================================

    /**
     * Extracts players from occupied spaces, sorting them by the space's position index.
     * This determines the new turn order for the next round.
     */
    public List<Player> getPlayersInOrder() {
        return this.offerSpaces.stream()
                .filter(space -> space.getOccupiedBy() != null)
                .sorted(Comparator.comparingInt(OfferSpace::getPositionIndex))
                .map(OfferSpace::getOccupiedBy)
                .collect(Collectors.toList());
    }

    /**
     * Clears all offer spaces by removing the assigned players.
     */
    public void clearOfferSpaces() {
        this.offerSpaces.forEach(OfferSpace::clear);
    }
}