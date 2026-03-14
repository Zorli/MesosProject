package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.List;

public class StartingTile {
    // List containing the starting blocks ordered from first to last
    private List<StartingBlock> startingBlocks;

    // ==========================================
    // CONSTRUCTOR
    // ==========================================

    public StartingTile() {
        this.startingBlocks = new ArrayList<>();
    }


    // ==========================================
    // INITIAL SETUP
    // ==========================================

    /**
     * Initializes the starting blocks based on the number of players.
     * Assigns the correct food and prestige modifiers, and physically
     * places the players on their initial blocks for the first round.
     */
    public void setup(List<Player> players) {
        int n = players.size();
        int[] foodModifiers;
        int[] prestigeModifiers;

        // Hardcoded balancing values. Can be moved to a JSON configuration later.
        if (n == 2) {
            foodModifiers = new int[]{ 1, -1 };
            prestigeModifiers = new int[]{ 0, 2 };
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

        // Creates the blocks and assigns the players immediately
        for (int i = 0; i < foodModifiers.length; i++) {
            int position = i + 1;
            int food = foodModifiers[i];
            int prestige = prestigeModifiers[i];
            Player startingPlayer = players.get(i);
            this.startingBlocks.add(new StartingBlock(position, food, prestige, startingPlayer));
        }
    }


    // ==========================================
    // END OF ROUND LOGIC (DOMINO EFFECT)
    // ==========================================

    /**
     * Receives the newly ordered list of players (e.g., from the TurnOrderTrack),
     * physically places them on the corresponding blocks, and resolves the effects.
     */
    public void placePlayersAndResolveEffects(List<Player> newTurnOrder) {
        this.placePlayers(newTurnOrder);
        this.resolveEffects();
    }

    /**
     * Clears all current blocks and re-occupies them based on the new turn order.
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
     * Resolves the end-of-round effects for players positioned on the blocks.
     * Handles food gain/loss and applies prestige penalties if food is insufficient.
     */
    private void resolveEffects() {
        for (StartingBlock block : this.startingBlocks) {
            Player player = block.getOccupiedBy();

            if (player != null) {
                int foodMod = block.getFoodModifier();

                if (foodMod > 0) {
                    player.gainFood(foodMod);
                }
                // CASE 2: The block requires food payment (Cost/Malus)
                else if (foodMod < 0) {
                    int foodCost = Math.abs(foodMod);

                    if (player.getFood() >= foodCost) {
                        player.payFood(foodCost);
                    } else {
                        // Player cannot pay the food cost, apply prestige penalty
                        int prestigePenalty = block.getPrestigeModifier();
                        player.gainPrestigePoints(prestigePenalty);

                    }
                }
            }
        }
    }
}