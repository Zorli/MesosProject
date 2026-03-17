package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the offer path where players place their totems to draft cards.
 * Also controls the starting tile, orchestrating the transition of players
 * between the starting blocks and the offer spaces, and determining the new turn order.
 */
public class OfferPath {
    private StartingTile startingTile;
    private List<OfferSpace> offerSpaces;

    /**
     * Initializes an empty offer path, automatically instantiating its internal
     * starting tile and the list to hold the offer spaces.
     */
    public OfferPath() {
        this.startingTile = new StartingTile();
        this.offerSpaces = new ArrayList<>();
    }

    /**
     * Configures the offer path and the starting tile for the beginning of the game,
     * tailoring the available spaces and blocks to the exact number of players.
     *
     * @param players The list of players in the game.
     */
    public void setup(List<Player> players) {
        int n = players.size();
        this.setupOfferSpaces(n);
        this.startingTile.setup(players);
    }

    /**
     * Populates the offer spaces list by filtering all possible hardcoded spaces
     * based on the minimum player requirement for the current game.
     *
     * @param numPlayer The total number of players in the game.
     */
    private void setupOfferSpaces(int numPlayer) {
        List<OfferSpace> allPossibleSpaces = List.of(
                new OfferSpace(1, "A", 5, 3, 0, 0),
                new OfferSpace(2, "B", 2, 0, 0, 1),
                new OfferSpace(3, "C", 2, 0, 1, 0),
                new OfferSpace(4, "D", 3, 0, 0, 2),
                new OfferSpace(5, "E", 2, 0, 1, 1),
                new OfferSpace(6, "F", 2, 0, 2, 0),
                new OfferSpace(7, "G", 4, 0, 2, 1)
        );

        for (OfferSpace space : allPossibleSpaces) {
            if (space.getMinPlayers() <= numPlayer) {
                this.offerSpaces.add(space);
            }
        }
    }

//    /**
//     * Executes the end-of-round logic for the path. It extracts the new turn order
//     * based on totem placements, clears the offer spaces, and relocates players
//     * back to the starting tile to resolve end-of-round prestige and food effects.
//     *
//     * @return The list of players sorted in their new turn order for the upcoming round.
//     */
//    public List<Player> resetAndGetNewOrder() {
//        List<Player> newOrder = this.getPlayersInOrder();
//
//        this.clearOfferSpaces();
//
//        this.startingTile.placePlayersAndResolveEffects(newOrder);
//
//        return newOrder;
//    }
    /**
     * Sposta un giocatore dallo spazio offerta in cui si trova alla tessera di partenza.
     * Libera lo spazio offerta e occupa il primo blocco di partenza disponibile.
     *
     * @param player Il giocatore che ha terminato il suo turno attivo.
     */
    public void returnPlayerToStartingTile(Player player) {
        // 1. Troviamo lo spazio offerta occupato dal giocatore e lo liberiamo
        this.offerSpaces.stream()
                .filter(space -> player.equals(space.getOccupiedBy()))
                .findFirst()
                .ifPresent(OfferSpace::clear);

        // 2. Lo piazziamo sul primo blocco libero della Starting Tile
        this.startingTile.placePlayerOnNextAvailableBlock(player);
    }

    /**
     * Determines the new turn order by extracting players from currently occupied spaces,
     * sorted from left to right (by the space's position index).
     *
     * @return A sorted list of players representing the turn order for the next round.
     */
    public List<Player> getPlayersInOrder() {
        return this.offerSpaces.stream()
                .filter(space -> space.getOccupiedBy() != null)
                .sorted(Comparator.comparingInt(OfferSpace::getPositionIndex))
                .map(OfferSpace::getOccupiedBy)
                .collect(Collectors.toList());
    }
    public boolean resolveEventA()
    {
        String targetCode = "A";
        return this.offerSpaces.stream()
                .filter(space -> targetCode.equals(space.getCode()) && !space.isAvailable())
                .findFirst()
                .map(space -> {
                    Player currentPlayer = space.getOccupiedBy();
                    space.resolveImmediateAction(currentPlayer);
                    return true;
                })
                .orElse(false);
    }
    /**
     * Removes all players from the offer spaces, making them available for the next round.
     */
    public void clearOfferSpaces() {
        this.offerSpaces.forEach(OfferSpace::clear);
    }

    /**
     * Checks if a specific offer space exists in the current game and is not already occupied.
     *
     * @param targetCode The unique string code of the target offer space (e.g., "A", "B").
     * @return True if the space is valid and currently available, false otherwise.
     */
    public boolean checkOfferSpace(String targetCode) {
        return this.offerSpaces.stream()
                .anyMatch(space -> targetCode.equals(space.getCode()) && space.isAvailable());
    }

    /**
     * Places a player's totem on the specified offer space, resolves any immediate
     * space effects (such as food bonuses), and retrieves the drafting values.
     *
     * @param player The player taking the space.
     * @param targetCode The unique string code of the target offer space.
     * @return An integer array containing the drafting constraints:
     * [0] = cards to draw from the upper row, [1] = cards to draw from the lower row.
     */
    public void placePlayer(Player player, String targetCode) {
        for (OfferSpace space : this.offerSpaces) {
            if (targetCode.equals(space.getCode())) {
                space.occupy(player);
                this.startingTile.removePlayer(player);
//                space.resolveImmediateAction(player);
                break;
            }
        }
    }
    /**
     * Risolve l'azione immediata (es. cibo) ESCLUSIVAMENTE per il giocatore specificato,
     * basandosi sullo spazio che occupa attualmente, e restituisce le regole di pescata.
     * * @param player Il giocatore di cui si sta risolvendo il turno attivo.
     * @return int[] {carteDaSopra, carteDaSotto}
     */
    public int[] resolveActionForPlayer(Player player) {
        return this.offerSpaces.stream()
                .filter(space -> player.equals(space.getOccupiedBy()))
                .findFirst()
                .map(space -> {
                    return new int[]{space.getTopRowCardsToTake(), space.getBottomRowCardsToTake()};
                })
                .orElse(new int[]{0, 0}); // Fallback di sicurezza
    }

    /**
     * Provides a read-only list of all offer spaces valid for the current game setup.
     * Useful for broadcasting the board state to clients or the game controller.
     *
     * @return A list of the current offer spaces.
     */
    public List<OfferSpace> getAvailableOfferSpaces() {
        return new ArrayList<>(this.offerSpaces);
    }
}