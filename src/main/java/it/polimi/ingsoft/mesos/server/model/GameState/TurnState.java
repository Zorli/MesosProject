package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.HashMap;
import java.util.Map;

public class TurnState {
    private TurnPhase phase;
    private Player activePlayer;
    private final Map<Player, Integer> remainingUpperChoicesByPlayer;
    private final Map<Player, Integer> remainingLowerChoicesByPlayer;

    // Constructors

    public TurnState() {
        this.phase = TurnPhase.TURN_COMPLETED;
        this.activePlayer = null;
        this.remainingUpperChoicesByPlayer = new HashMap<>();
        this.remainingLowerChoicesByPlayer = new HashMap<>();
    }

    /**
     * Creates a turn state from an already persisted runtime snapshot.
     *
     * @param phase turn phase to restore
     * @param activePlayer active player to restore, may be null
     * @param remainingUpperChoicesByPlayer remaining upper-row choices by player
     * @param remainingLowerChoicesByPlayer remaining lower-row choices by player
     */
    public TurnState(TurnPhase phase,
                     Player activePlayer,
                     Map<Player, Integer> remainingUpperChoicesByPlayer,
                     Map<Player, Integer> remainingLowerChoicesByPlayer) {
        this.phase = phase;
        this.activePlayer = activePlayer;
        this.remainingUpperChoicesByPlayer = new HashMap<>(remainingUpperChoicesByPlayer);
        this.remainingLowerChoicesByPlayer = new HashMap<>(remainingLowerChoicesByPlayer);
    }

    // Getters

    public TurnPhase getPhase() {
        return this.phase;
    }

    public Player getActivePlayer() {
        return this.activePlayer;
    }

    public int getRemainingUpperChoices(Player player) {
        return this.remainingUpperChoicesByPlayer.getOrDefault(player, 0);
    }

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

    public boolean hasDrawableCards(Player player) {
        return this.getRemainingUpperChoices(player) > 0 || this.getRemainingLowerChoices(player) > 0;
    }

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

    public void startCardSelection(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        if (!this.remainingUpperChoicesByPlayer.containsKey(player)
                || !this.remainingLowerChoicesByPlayer.containsKey(player)) {
            throw new IllegalStateException("Drawable cards were not registered for player " + player.getNickname());
        }

        this.activePlayer = player;
        this.phase = TurnPhase.WAITING_FOR_CARD_SELECTION;
    }

    public void resetForNewRound() {
        this.phase = TurnPhase.TURN_COMPLETED;
        this.activePlayer = null;
        this.remainingUpperChoicesByPlayer.clear();
        this.remainingLowerChoicesByPlayer.clear();
    }

    public void resetForNextPlayer() {
        if (this.activePlayer != null) {
            this.remainingUpperChoicesByPlayer.remove(this.activePlayer);
            this.remainingLowerChoicesByPlayer.remove(this.activePlayer);
        }
        this.phase = TurnPhase.TURN_COMPLETED;
        this.activePlayer = null;
    }
}
