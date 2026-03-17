package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.List;

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
     * Rimuove il giocatore dal suo blocco di partenza corrente.
     * Viene chiamato nel momento in cui il giocatore muove il suo totem verso un'offerta.
     *
     * @param player Il giocatore che sta lasciando la tessera di partenza.
     */
    public void removePlayer(Player player) {
        this.startingBlocks.stream()
                .filter(block -> player.equals(block.getOccupiedBy()))
                .findFirst()
                .ifPresent(StartingBlock::clear);
    }

    /**
     * Receives the newly determined turn order, places players onto the starting blocks,
     * and resolves the start-of-round block effects.
     *
     * @param newTurnOrder players sorted in the order they should occupy the starting tile
     */
    public void placePlayersAndResolveEffects(List<Player> newTurnOrder) {
        this.placePlayers(newTurnOrder);
        this.resolveEffects();
    }

    private void placePlayers(List<Player> orderedPlayers) {
        this.startingBlocks.forEach(StartingBlock::clear);

        for (int i = 0; i < orderedPlayers.size(); i++) {
            if (i < this.startingBlocks.size()) {
                StartingBlock currentBlock = this.startingBlocks.get(i);
                Player currentPlayer = orderedPlayers.get(i);
                currentBlock.occupy(currentPlayer);
            }
        }
    }

    private void resolveEffects() {
        for (StartingBlock block : this.startingBlocks) {
            Player player = block.getOccupiedBy();

            if (player == null) {
                continue;
            }

            int foodMod = block.getFoodModifier();
            if (foodMod > 0) {
                player.gainFood(foodMod);
            } else if (foodMod < 0) {
                int foodCost = Math.abs(foodMod);

                if (player.getFood() >= foodCost) {
                    player.payFood(foodCost);
                } else {
                    player.gainPrestigePoints(block.getPrestigeModifier());
                }
            }
        }
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
