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

    /**
     * Receives the newly determined turn order, physically places the players onto
     * the corresponding starting blocks, and immediately resolves their end-of-round effects.
     *
     * @param newTurnOrder The list of players sorted in their new turn order for the next round.
     */
    public void placePlayersAndResolveEffects(List<Player> newTurnOrder) {
        this.placePlayers(newTurnOrder);
        this.resolveEffects();
    }

    /**
     * Clears all current starting blocks and reassigns them to the players
     * according to the newly established turn order.
     *
     * @param orderedPlayers The list of players to place on the blocks.
     */
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

    /**
     * Resolves the end-of-round effects for all players currently occupying the starting blocks.
     * Applies food bonuses, deducts food costs, or applies prestige penalties if a player
     * cannot afford the required food cost for their position.
     */
    private void resolveEffects() {
        for (StartingBlock block : this.startingBlocks) {
            Player player = block.getOccupiedBy();

            if (player != null) {
                int foodMod = block.getFoodModifier();

                if (foodMod > 0) {
                    player.gainFood(foodMod);
                } else if (foodMod < 0) {
                    int foodCost = Math.abs(foodMod);

                    if (player.getFood() >= foodCost) {
                        player.payFood(foodCost);
                    } else {
                        int prestigePenalty = block.getPrestigeModifier();
                        player.gainPrestigePoints(prestigePenalty);
                    }
                }
            }
        }
    }
}