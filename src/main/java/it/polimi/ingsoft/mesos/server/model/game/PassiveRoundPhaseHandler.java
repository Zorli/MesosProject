package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.enums.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;

/**
 * Advances the automatic phases of a round.
 *
 * <p>Most game phases are driven by a player command. For example,
 * {@code moveTotem}, {@code chooseCard}, and {@code extraDrawn} are explicit
 * actions received from a client.
 *
 * <p>Some phases are different: they do not require another player command.
 * Once the game reaches one of those phases, the model can continue by itself.
 * Those automatic phases are:
 * <ul>
 *     <li>{@link RoundPhase#RESOLVING_EFFECTS};</li>
 *     <li>{@link RoundPhase#RESETTING_BOARD};</li>
 *     <li>{@link RoundPhase#FINAL_SCORING}.</li>
 * </ul>
 *
 * <p>This handler owns only that passive progression. It changes model state
 * and marks the game as changed, but it does not notify listeners. Listener
 * notification belongs to {@link GameActionExecutor}, which wraps the whole
 * public action and sends one final payload if the state version changed.
 */
public class PassiveRoundPhaseHandler {
    private static final int FINAL_ROUND_NUMBER = 10;

    private final Game game;
    private final Board board;
    private final RoundFlow roundFlow;
    private final PlayerDraftState playerDraftState;

    /**
     * Creates the handler used to advance passive round phases.
     *
     * @param game game aggregate that owns lifecycle status, era, players, and state version
     * @param board board that resolves events and resets visible rows
     * @param roundFlow object that knows current round number and current phase
     * @param playerDraftState object that must be reset when a new round starts
     */
    public PassiveRoundPhaseHandler(Game game,
                                    Board board,
                                    RoundFlow roundFlow,
                                    PlayerDraftState playerDraftState) {
        if (game == null) {
            throw new IllegalArgumentException("Game cannot be null");
        }
        if (board == null) {
            throw new IllegalArgumentException("Board cannot be null");
        }
        if (roundFlow == null) {
            throw new IllegalArgumentException("Round flow cannot be null");
        }
        if (playerDraftState == null) {
            throw new IllegalArgumentException("Player draft state cannot be null");
        }

        this.game = game;
        this.board = board;
        this.roundFlow = roundFlow;
        this.playerDraftState = playerDraftState;
    }

    /**
     * Keeps advancing the game while the current phase is automatic.
     *
     * <p>The method stops as soon as the game reaches a phase that requires a
     * player command. For example, after resetting the board, the game returns
     * to {@link RoundPhase#PLACING_TOTEMS}; at that point the next player must
     * actively choose a totem space, so this method stops.
     */
    public void advancePassivePhases() {
        while (this.game.getStatus() == GameStatus.IN_PROGRESS) {
            RoundPhase currentPhase = this.roundFlow.getCurrentRoundPhase();

            if (currentPhase == RoundPhase.RESOLVING_EFFECTS) {
                this.resolveEffects();
            } else if (currentPhase == RoundPhase.RESETTING_BOARD) {
                this.resetBoardForNextRound();
            } else if (currentPhase == RoundPhase.FINAL_SCORING) {
                this.calculateFinalScore();
            } else {
                return;
            }
        }
    }

    /**
     * Resolves the event phase of the round.
     *
     * <p>During rounds before the last one, only lower-row events are resolved
     * and the board then moves to the reset phase. During the final round, all
     * visible events are resolved and the game moves directly to final scoring.
     */
    private void resolveEffects() {
        if (this.roundFlow.getRoundNumber() == FINAL_ROUND_NUMBER) {
            this.board.resolveAllEvents();
            this.roundFlow.setCurrentRoundPhase(RoundPhase.FINAL_SCORING);
            this.game.markStateChanged();
            return;
        }

        this.board.resolveLowerEvents();
        this.roundFlow.setCurrentRoundPhase(RoundPhase.RESETTING_BOARD);
        this.game.markStateChanged();
    }

    /**
     * Cleans and refills the board for the next round.
     *
     * <p>The board owns the card rows and decks, so it decides whether refilling
     * cards caused an era change. The game owns the current era value, so this
     * handler asks {@link Game} to advance the era when the board reports that
     * a new era was reached.
     *
     * <p>After the board reset, the round number increases, all standard draft
     * quotas are cleared, and the round returns to totem placement.
     */
    private void resetBoardForNextRound() {
        boolean eraChanged = this.board.resetBoardAfterRound(this.game.getCurrentEra());

        if (eraChanged) {
            this.game.advanceEra();
        }

        this.roundFlow.advanceRoundNumber();
        this.playerDraftState.resetForNewRound();
        this.roundFlow.setCurrentRoundPhase(RoundPhase.PLACING_TOTEMS);
        this.game.markStateChanged();
    }

    /**
     * Applies final scoring and marks the game as finished.
     *
     * <p>Each building can contribute final prestige points and may also apply a
     * final effect. After all buildings are resolved, the tribe builder bonus is
     * added. When all players have been scored, the game status becomes
     * {@link GameStatus#FINISHED}.
     */
    private void calculateFinalScore() {
        for (Player player : this.game.getPlayers()) {
            this.applyFinalBuildingScore(player);
            this.applyFinalBuilderBonus(player);
        }

        this.game.finishGame();
        this.game.markStateChanged();
    }

    /**
     * Applies every building contribution owned by one player.
     *
     * <p>The building itself knows how many final prestige points it grants and
     * whether it has an additional effect. This handler only iterates through
     * the player's buildings in order and delegates the building-specific rule.
     *
     * @param player player whose buildings are being scored
     */
    private void applyFinalBuildingScore(Player player) {
        for (BuildingCard building : player.getTribe().getBuildings()) {
            building.increaseFinalPrestigePoints(player);
            building.applyEffect(player);
        }
    }

    /**
     * Adds the final prestige bonus earned from the player's builder cards.
     *
     * @param player player receiving the builder final bonus
     */
    private void applyFinalBuilderBonus(Player player) {
        int builderPrestigePoints = player.getTribe().getPrestigePointsForBuilderInFinalPhase();
        player.gainPrestigePoints(builderPrestigePoints);
    }
}
