package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Finite-state machine that tracks what the active player may still do during card drafting.
 *
 * <p>The state keeps per-player counters for upper and lower row choices and exposes helpers used
 * by {@link Game} to validate transitions from totem placement to card selection and finally to
 * turn completion.
 */
public class TurnState {
    private TurnPhase phase;
    private final Map<Player, Integer> remainingUpperChoicesByPlayer;
    private final Map<Player, Integer> remainingLowerChoicesByPlayer;


    /**
     * Creates an empty turn state with no registered draft choices.
     */
    public TurnState() {
        this.phase = TurnPhase.TURN_COMPLETED;
        this.remainingUpperChoicesByPlayer = new HashMap<>();
        this.remainingLowerChoicesByPlayer = new HashMap<>();
    }

    /**
     * Creates a turn state from an already persisted runtime snapshot.
     *
     * @param phase turn phase to restore
     * @param remainingUpperChoicesByPlayer remaining upper-row choices by player
     * @param remainingLowerChoicesByPlayer remaining lower-row choices by player
     */
    public TurnState(TurnPhase phase,
                     Map<Player, Integer> remainingUpperChoicesByPlayer,
                     Map<Player, Integer> remainingLowerChoicesByPlayer) {
        this.phase = phase;
        this.remainingUpperChoicesByPlayer = new HashMap<>(remainingUpperChoicesByPlayer);
        this.remainingLowerChoicesByPlayer = new HashMap<>(remainingLowerChoicesByPlayer);
    }



    /**
     * Returns the current turn phase tracked by the state machine.
     *
     * @return active turn phase
     */
    public TurnPhase getPhase() {
        return this.phase;
    }

    /**
     * Returns how many upper-row cards the given player may still draw this turn.
     *
     * @param player player to inspect
     * @return remaining upper-row choices, or {@code 0} when none are registered
     */
    public int getRemainingUpperChoices(Player player) {
        return this.remainingUpperChoicesByPlayer.getOrDefault(player, 0);
    }

    /**
     * Returns how many lower-row cards the given player may still draw this turn.
     *
     * @param player player to inspect
     * @return remaining lower-row choices, or {@code 0} when none are registered
     */
    public int getRemainingLowerChoices(Player player) {
        return this.remainingLowerChoicesByPlayer.getOrDefault(player, 0);
    }

    /**
     * Returns a copy of the remaining upper choices tracked for all players.
     *
     * @return copy of upper-row choice counters by player
     */
    public Map<Player, Integer> getRemainingUpperChoicesByPlayer() {
        return new HashMap<>(this.remainingUpperChoicesByPlayer);
    }

    /**
     * Returns a copy of the remaining lower choices tracked for all players.
     *
     * @return copy of lower-row choice counters by player
     */
    public Map<Player, Integer> getRemainingLowerChoicesByPlayer() {
        return new HashMap<>(this.remainingLowerChoicesByPlayer);
    }

    // Public methods

    /**
     * Returns whether the player still has any drawable card choices left for the turn.
     *
     * @param player player to inspect
     * @return {@code true} when at least one upper or lower choice remains
     */
    public boolean hasDrawableCards(Player player) {
        return this.getRemainingUpperChoices(player) > 0 || this.getRemainingLowerChoices(player) > 0;
    }

    /**
     * Returns whether the state machine is currently waiting for a card selection.
     *
     * @return {@code true} when the active player may choose a card
     */
    public boolean isWaitingForCardSelection() {
        return this.phase == TurnPhase.WAITING_FOR_CARD_SELECTION;
    }

    /**
     * Returns whether the tracked turn has already been completed.
     *
     * @return {@code true} when no more card selections are pending
     */
    public boolean isTurnCompleted() {
        return this.phase == TurnPhase.TURN_COMPLETED;
    }

    /**
     * Returns whether the player may currently draw from the upper row.
     *
     * @param player player attempting the draw
     * @return {@code true} when the state allows one more upper-row choice
     */
    public boolean canChooseFromUpperRow(Player player) {
        return player != null
                && this.phase == TurnPhase.WAITING_FOR_CARD_SELECTION
                && this.getRemainingUpperChoices(player) > 0;
    }

    /**
     * Returns whether the player may currently draw from the lower row.
     *
     * @param player player attempting the draw
     * @return {@code true} when the state allows one more lower-row choice
     */
    public boolean canChooseFromLowerRow(Player player) {
        return player != null
                && this.phase == TurnPhase.WAITING_FOR_CARD_SELECTION
                && this.getRemainingLowerChoices(player) > 0;
    }

    /**
     * Registers how many cards the player may draw from each board row this turn.
     *
     * @param player player whose counters must be initialized
     * @param upperChoices number of remaining upper-row choices
     * @param lowerChoices number of remaining lower-row choices
     */
    public void registerDrawableCards(Player player, int upperChoices, int lowerChoices) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        if (upperChoices < 0 || lowerChoices < 0) {
            throw new IllegalArgumentException("Choices must be non-negative");
        }

        this.remainingUpperChoicesByPlayer.put(player, upperChoices);
        this.remainingLowerChoicesByPlayer.put(player, lowerChoices);
    }

    /**
     * Transitions the state machine into card-selection mode for the given player.
     *
     * @param player player whose registered draw choices are about to be consumed
     */
    public void startCardSelection(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        if (!this.remainingUpperChoicesByPlayer.containsKey(player)
                || !this.remainingLowerChoicesByPlayer.containsKey(player)) {
            throw new IllegalStateException("Drawable cards were not registered for player " + player.getNickname());
        }

        this.phase = TurnPhase.WAITING_FOR_CARD_SELECTION;
    }

    /**
     * Consumes one draw choice from the selected row and completes the turn when no choices remain.
     *
     * @param player player who selected a card
     * @param cardRow row the chosen card came from
     */
    public void onCardChosen(Player player, CardRow cardRow) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        if (cardRow == CardRow.UPPER) {
            if (!this.canChooseFromUpperRow(player)) {
                throw new IllegalStateException("Cannot choose from upper row");
            }
            this.remainingUpperChoicesByPlayer.put(
                    player,
                    this.getRemainingUpperChoices(player) - 1
            );
        } else if (cardRow == CardRow.LOWER) {
            if (!this.canChooseFromLowerRow(player)) {
                throw new IllegalStateException("Cannot choose from lower row");
            }
            this.remainingLowerChoicesByPlayer.put(
                    player,
                    this.getRemainingLowerChoices(player) - 1
            );
        } else {
            throw new IllegalArgumentException("Card row cannot be NOT_PRESENT");
        }

        if (!this.hasDrawableCards(player)) {
            this.phase = TurnPhase.TURN_COMPLETED;
        }
    }

    /**
     * Clears all counters and returns the state machine to the idle round-start state.
     */
    public void resetForNewRound() {
        this.phase = TurnPhase.TURN_COMPLETED;
        this.remainingUpperChoicesByPlayer.clear();
        this.remainingLowerChoicesByPlayer.clear();
    }

    /**
     * Clears the counters of the specified player and marks the turn as completed.
     *
     * @param player player whose per-turn counters should be removed
     */
    public void resetForNextPlayer(Player player) {
        if (player != null) {
            this.remainingUpperChoicesByPlayer.remove(player);
            this.remainingLowerChoicesByPlayer.remove(player);
        }
        this.phase = TurnPhase.TURN_COMPLETED;
    }
}
