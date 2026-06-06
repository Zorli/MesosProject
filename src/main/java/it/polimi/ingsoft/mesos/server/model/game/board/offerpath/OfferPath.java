package it.polimi.ingsoft.mesos.server.model.game.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.game.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    /**
     * Moves a player from their current offer space back to the starting tile.
     * Clears the occupied offer space and automatically places the player on the first available starting block.
     *
     * @param player The player who has just completed their active turn.
     */
    public void returnPlayerToStartingTile(Player player) {
        // 1. Find the offer space occupied by the player and clear it
        this.offerSpaces.stream()
                .filter(space -> player.equals(space.getOccupiedBy()))
                .findFirst()
                .ifPresent(OfferSpace::clear);

        // 2. Place the player on the first available block of the Starting Tile
        this.startingTile.placePlayerOnNextAvailableBlock(player);
    }

    /**
     * Removes the player from their current location and places them on the last
     * available starting block (the lowest position in the next round's turn order).
     *
     * @param player the player to move to the last available starting block
     * @param from origin of the player, used to know which location to clear before the move:
     *             {@code 0} if the player is currently on an offer space (the occupied offer
     *             space is cleared), {@code 1} if the player is currently on the starting tile
     *             (the player is removed from their current starting block)
     */
    public void movePlayerToLastStartingTile(Player player, int from) {
        // clear the offer space or starting block where the player was: from = 0 means offer path, from = 1 means starting tile
        if(from == 1)
        {
            this.startingTile.removePlayer(player);
        }
        else if(from == 0){
            this.offerSpaces.stream().filter(space -> player.equals(space.getOccupiedBy())).findFirst().ifPresent(OfferSpace::clear);
        }
        this.startingTile.placePlayerOnLastAvailableBlock(player);
    }
    /**
     * Determines the new turn order by extracting players from currently occupied spaces,
     * sorted from left to right (by the space's position index), it skips offer space A
     * as it was already resolved
     *
     * @return A sorted list of players representing the turn order for the next turn step.
     */
    public List<Player> getPlayersInOrder() {
        return this.offerSpaces.stream()
                .filter(space -> space.getOccupiedBy() != null && !"A".equals(space.getCode()))
                .sorted(Comparator.comparingInt(OfferSpace::getPositionIndex))
                .map(OfferSpace::getOccupiedBy)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the turn order for the upcoming round based on the players'
     * positions on the starting tile blocks.
     *
     * @return A list of players sorted by their new turn order.
     */
    public List<Player> getNextRoundTurnOrder() {
        return this.startingTile.getNextRoundOrder();
    }

    /**
     * Checks if the offer space 'A' is currently occupied by a player.
     * If occupied, it immediately resolves the space's specific effect for that player
     * and returns the player to the starting tile . If the space is empty or unavailable,
     * it does nothing.
     *
     */
    public void resolveEventA() {
        String targetCode = "A";
        this.offerSpaces.stream()
                .filter(space -> targetCode.equals(space.getCode()) && !space.isAvailable())
                .findFirst()
                .ifPresent(space -> {
                    Player currentPlayer = space.getOccupiedBy();
                    space.resolveImmediateAction(currentPlayer);
                    returnPlayerToStartingTile(currentPlayer);
                });
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
     * Places a player's totem on the specified offer space.
     *
     * @param player The player taking the space.
     * @param targetCode The unique string code of the target offer space.
     */
    public void placePlayer(Player player, String targetCode) {
        for (OfferSpace space : this.offerSpaces) {
            if (targetCode.equals(space.getCode())) {
                space.occupy(player);
                this.startingTile.removePlayer(player);
                break;
            }
        }
    }

    /**
     * Returns the drafting rules for the given player, read from the offer space they
     * currently occupy. This method only reads and returns the values; it does not apply
     * or resolve any effect.
     *
     * <p>If the player does not occupy any offer space, {@code {0, 0}} is returned as a
     * safety fallback.
     *
     * @param player the player whose occupied offer space is inspected
     * @return a two-element {@code int[]} where index 0 is the number of cards to draft from
     *         the upper row and index 1 is the number of cards to draft from the lower row
     */
    public int[] resolveActionForPlayer(Player player) {
        return this.offerSpaces.stream()
                .filter(space -> player.equals(space.getOccupiedBy()))
                .findFirst()
                .map(space -> new int[]{space.getTopRowCardsToTake(), space.getBottomRowCardsToTake()})
                .orElse(new int[]{0, 0}); // Safety fallback
    }

    /**
     * Provides a read-only list of all offer spaces that are currently FREE to be occupied.
     * Useful for broadcasting the valid placement options to clients.
     *
     * @return A list of the currently available offer spaces.
     */
    public List<OfferSpace> getAvailableOfferSpaces() {
        return this.offerSpaces.stream()
                .filter(OfferSpace::isAvailable)
                .collect(Collectors.toList());
    }

    /**
     * Returns all offer spaces configured for the current match, including occupied ones.
     *
     * @return copy of all offer spaces in path order
     */
    public List<OfferSpace> getOfferSpaces() {
        return new ArrayList<>(this.offerSpaces);
    }

    /**
     * Returns the starting tile associated with this offer path.
     *
     * @return starting tile
     */
    public StartingTile getStartingTile() {
        return this.startingTile;
    }
}
