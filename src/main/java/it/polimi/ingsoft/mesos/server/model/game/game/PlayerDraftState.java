package it.polimi.ingsoft.mesos.server.model.game.game;

import it.polimi.ingsoft.mesos.server.model.game.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.game.game.enums.DraftTurnState;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks the standard card draw quotas of players during a round.
 *
 * <p>After all players have placed their totems, the board knows how many cards
 * each player may draw from the upper row and from the lower row. This class
 * stores those numbers and consumes them one card at a time.
 *
 * <p>This class deliberately knows only the draw quotas. It does not know:
 * <ul>
 *     <li>which player is the current player;</li>
 *     <li>which cards are present on the board;</li>
 *     <li>whether a specific card is drawable;</li>
 *     <li>when a player must return to the starting tile;</li>
 *     <li>who is allowed to perform an extra draw.</li>
 * </ul>
 *
 * <p>Extra draws are not tracked here because they do not consume the standard
 * upper-row and lower-row quotas.
 */
public class PlayerDraftState {
    private DraftTurnState phase;
    private final Map<Player, Integer> remainingUpperChoicesByPlayer;
    private final Map<Player, Integer> remainingLowerChoicesByPlayer;

    /**
     * Creates an empty draft state.
     *
     * <p>No player is currently choosing a card and no draw quota is registered.
     */
    public PlayerDraftState() {
        this.phase = DraftTurnState.TURN_COMPLETED;
        this.remainingUpperChoicesByPlayer = new HashMap<>();
        this.remainingLowerChoicesByPlayer = new HashMap<>();
    }

    /**
     * Returns the current card-selection state.
     *
     * @return current draft turn state
     */
    public DraftTurnState getPhase() {
        return this.phase;
    }

    /**
     * Returns how many upper-row cards the player may still draw.
     *
     * @param player player to inspect
     * @return remaining upper-row choices, or {@code 0} if no quota is registered
     */
    public int getRemainingUpperChoices(Player player) {
        return this.remainingUpperChoicesByPlayer.getOrDefault(player, 0);
    }

    /**
     * Returns how many lower-row cards the player may still draw.
     *
     * @param player player to inspect
     * @return remaining lower-row choices, or {@code 0} if no quota is registered
     */
    public int getRemainingLowerChoices(Player player) {
        return this.remainingLowerChoicesByPlayer.getOrDefault(player, 0);
    }

    /**
     * Returns a copy of all upper-row quotas.
     *
     * @return copy of upper-row quotas by player
     */
    public Map<Player, Integer> getRemainingUpperChoicesByPlayer() {
        return new HashMap<>(this.remainingUpperChoicesByPlayer);
    }

    /**
     * Returns a copy of all lower-row quotas.
     *
     * @return copy of lower-row quotas by player
     */
    public Map<Player, Integer> getRemainingLowerChoicesByPlayer() {
        return new HashMap<>(this.remainingLowerChoicesByPlayer);
    }

    /**
     * Registers the standard draw quotas earned by a player after totem placement.
     *
     * @param player player whose quotas must be stored
     * @param upperChoices number of cards the player may draw from the upper row
     * @param lowerChoices number of cards the player may draw from the lower row
     */
    public void registerDrawQuota(Player player, int upperChoices, int lowerChoices) {
        this.validatePlayer(player);

        if (upperChoices < 0 || lowerChoices < 0) {
            throw new IllegalArgumentException("Choices must be non-negative");
        }

        this.remainingUpperChoicesByPlayer.put(player, upperChoices);
        this.remainingLowerChoicesByPlayer.put(player, lowerChoices);
    }

    /**
     * Starts the card-selection phase for a player.
     *
     * <p>The player must already have both upper-row and lower-row quotas
     * registered. A quota can be zero, but the entry must exist because it means
     * the board has already resolved that player's totem space.
     *
     * @param player player who is about to choose cards
     */
    public void startCardSelection(Player player) {
        this.validatePlayer(player);

        if (!this.hasRegisteredQuota(player)) {
            throw new IllegalStateException("Drawable cards were not registered for player " + player.getNickname());
        }

        this.phase = DraftTurnState.WAITING_FOR_CARD_SELECTION;
    }

    /**
     * Returns whether the state is waiting for a card choice.
     *
     * @return {@code true} if a player can currently choose a card
     */
    public boolean isWaitingForCardSelection() {
        return this.phase == DraftTurnState.WAITING_FOR_CARD_SELECTION;
    }

    /**
     * Returns whether the current card-selection turn is completed.
     *
     * @return {@code true} if no card selection is currently active
     */
    public boolean isTurnCompleted() {
        return this.phase == DraftTurnState.TURN_COMPLETED;
    }

    /**
     * Returns whether the player still has at least one standard draw quota.
     *
     * @param player player to inspect
     * @return {@code true} if an upper-row or lower-row quota is still positive
     */
    public boolean hasRemainingChoices(Player player) {
        return this.getRemainingUpperChoices(player) > 0
                || this.getRemainingLowerChoices(player) > 0;
    }

    /**
     * Returns whether the player may consume one upper-row choice now.
     *
     * @param player player attempting to draw from the upper row
     * @return {@code true} if upper-row drawing is currently allowed
     */
    public boolean canChooseFromUpperRow(Player player) {
        return player != null
                && this.isWaitingForCardSelection()
                && this.getRemainingUpperChoices(player) > 0;
    }

    /**
     * Returns whether the player may consume one lower-row choice now.
     *
     * @param player player attempting to draw from the lower row
     * @return {@code true} if lower-row drawing is currently allowed
     */
    public boolean canChooseFromLowerRow(Player player) {
        return player != null
                && this.isWaitingForCardSelection()
                && this.getRemainingLowerChoices(player) > 0;
    }

    /**
     * Consumes one standard draw choice from the selected row.
     *
     * <p>If the player has no choices left after this consumption, the state
     * returns to {@link DraftTurnState#TURN_COMPLETED}.
     *
     * @param player player who has just drawn a card
     * @param cardRow row from which the card was drawn
     */
    public void consumeChoice(Player player, CardRow cardRow) {
        this.validatePlayer(player);

        if (cardRow == CardRow.UPPER) {
            this.consumeUpperChoice(player);
        } else if (cardRow == CardRow.LOWER) {
            this.consumeLowerChoice(player);
        } else {
            throw new IllegalArgumentException("Card row cannot be NOT_PRESENT");
        }

        if (!this.hasRemainingChoices(player)) {
            this.phase = DraftTurnState.TURN_COMPLETED;
        }
    }

    /**
     * Clears all player quotas and marks the draft state as completed.
     */
    public void resetForNewRound() {
        this.phase = DraftTurnState.TURN_COMPLETED;
        this.remainingUpperChoicesByPlayer.clear();
        this.remainingLowerChoicesByPlayer.clear();
    }

    /**
     * Clears one player's quotas and marks the draft state as completed.
     *
     * @param player player whose quotas must be removed
     */
    public void resetForNextPlayer(Player player) {
        if (player != null) {
            this.remainingUpperChoicesByPlayer.remove(player);
            this.remainingLowerChoicesByPlayer.remove(player);
        }

        this.phase = DraftTurnState.TURN_COMPLETED;
    }

    /**
     * Consumes one upper-row choice for the player.
     *
     * <p>The method first checks the current draft phase and the remaining
     * upper-row quota. If the choice is legal, the stored counter is decreased
     * by one. It does not decide whether the player's whole turn is completed;
     * that decision is made by {@link #consumeChoice(Player, CardRow)} after the
     * row-specific consumption.
     *
     * @param player player who has drawn from the upper row
     */
    private void consumeUpperChoice(Player player) {
        if (!this.canChooseFromUpperRow(player)) {
            throw new IllegalStateException("Cannot choose from upper row");
        }

        int remainingChoices = this.getRemainingUpperChoices(player) - 1;
        this.remainingUpperChoicesByPlayer.put(player, remainingChoices);
    }

    /**
     * Consumes one lower-row choice for the player.
     *
     * <p>The method mirrors {@link #consumeUpperChoice(Player)} for the lower
     * row: it validates that the lower choice is allowed and then decreases the
     * stored lower-row counter by one.
     *
     * @param player player who has drawn from the lower row
     */
    private void consumeLowerChoice(Player player) {
        if (!this.canChooseFromLowerRow(player)) {
            throw new IllegalStateException("Cannot choose from lower row");
        }

        int remainingChoices = this.getRemainingLowerChoices(player) - 1;
        this.remainingLowerChoicesByPlayer.put(player, remainingChoices);
    }

    /**
     * Returns whether the player already has both quota counters registered.
     *
     * <p>A player can legitimately have zero upper choices or zero lower
     * choices. For that reason this method checks map presence, not whether the
     * values are positive.
     *
     * @param player player to inspect
     * @return {@code true} if both upper and lower counters exist
     */
    private boolean hasRegisteredQuota(Player player) {
        return this.remainingUpperChoicesByPlayer.containsKey(player)
                && this.remainingLowerChoicesByPlayer.containsKey(player);
    }

    /**
     * Rejects null players before using them as map keys.
     *
     * <p>The draft state is keyed by {@link Player}. A null key would hide a
     * caller bug and make the turn state harder to reason about, so public
     * methods call this helper before reading or writing quota maps.
     *
     * @param player player argument to validate
     */
    private void validatePlayer(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
    }
}
