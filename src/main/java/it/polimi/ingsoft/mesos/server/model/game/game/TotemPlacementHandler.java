package it.polimi.ingsoft.mesos.server.model.game.game;

import it.polimi.ingsoft.mesos.server.model.game.board.Board;
import it.polimi.ingsoft.mesos.server.model.game.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.game.enums.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidGamePhaseException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidGameStatusException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.NotPlayersTurnException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.TileNotAvailableException;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

import java.util.List;

/**
 * Executes the totem-placement action of a round.
 *
 * <p>In Mesos, every round starts with the players placing their totems on the
 * offer path. The offer path decides two important things:
 * <ul>
 *     <li>which immediate effect the player receives from the chosen space;</li>
 *     <li>how many cards the player may later draw from the upper and lower
 *     card rows.</li>
 * </ul>
 *
 * <p>This class owns only that part of the game flow. It validates that the
 * player is allowed to place a totem now, places the totem on the board, stores
 * the standard draw quotas, and advances the round flow to the next player.
 *
 * <p>When the last totem has been placed, the class prepares the next phase:
 * it resolves space A, asks the board for the offer-path resolution order, and
 * changes the round phase from {@link RoundPhase#PLACING_TOTEMS} to
 * {@link RoundPhase#PLAYER_DRAWS}.
 *
 * <p>Disconnected players are handled only at the turn-order level during this
 * phase. A player who has already placed a totem remains on the chosen offer
 * space. A player who has not placed a totem remains on the starting tile. The
 * board is not used as a temporary parking area for disconnected players.
 *
 * <p>This class deliberately does not choose cards, resolve card effects,
 * manage extra draws, reset the board, or compute the final score. Those steps
 * belong to different handlers so that each rule stays small and readable.
 */
public class TotemPlacementHandler {
    private final Game game;
    private final Board board;
    private final RoundFlow roundFlow;
    private final PlayerDraftState playerDraftState;

    /**
     * Creates the handler used to execute totem placement.
     *
     * @param game game aggregate that owns status and state-version changes
     * @param board board on which the totem is placed
     * @param roundFlow object that knows the current phase and current player
     * @param playerDraftState object that stores the card draw quotas earned by players
     */
    public TotemPlacementHandler(Game game,
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
     * Places the current player's totem on an offer space.
     *
     * <p>The action is legal only when:
     * <ul>
     *     <li>the game has already started;</li>
     *     <li>the round is in the totem-placement phase;</li>
     *     <li>the requesting player is the current player;</li>
     *     <li>the requested offer space exists and is free.</li>
     * </ul>
     *
     * <p>After a legal placement, the board returns the number of upper-row and
     * lower-row cards that the player may draw later in the same round. Those
     * numbers are stored in {@link PlayerDraftState}.
     *
     * @param player player placing the totem
     * @param tileCode code of the target offer space, for example {@code "A"}
     */
    public void moveTotem(Player player, String tileCode) {
        this.validateMoveTotemRequest(player, tileCode);
        this.placeTotemAndRegisterDrawQuota(player, tileCode);
        this.advanceAfterTotemPlacement();
        this.game.markStateChanged();
    }

    /**
     * Checks whether the totem-placement command is legal before changing state.
     *
     * <p>The validation follows the same order as the game rules:
     * the match must be running, the round must be in the totem-placement phase,
     * the requester must be the player whose turn is active, and the requested
     * offer space must exist and still be free.
     *
     * @param player player asking to place the totem
     * @param tileCode offer-space code selected by the player
     */
    private void validateMoveTotemRequest(Player player, String tileCode) {
        if (this.game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new InvalidGameStatusException("moveTotem", this.game.getStatus());
        }

        if (this.roundFlow.getCurrentRoundPhase() != RoundPhase.PLACING_TOTEMS) {
            throw new InvalidGamePhaseException("moveTotem", this.roundFlow.getCurrentRoundPhase());
        }

        Player currentPlayer = this.roundFlow.getCurrentPlayer();
        if (!currentPlayer.equals(player)) {
            throw new NotPlayersTurnException(currentPlayer, player);
        }

        if (tileCode == null || !this.board.isSpaceAvailable(tileCode)) {
            throw new TileNotAvailableException(tileCode);
        }
    }

    /**
     * Applies the physical totem placement and stores the draw quota earned.
     *
     * <p>The board owns the offer path, so it physically places the player on
     * the chosen space. The same space defines how many upper-row and lower-row
     * cards the player may draw later. Those numbers are copied into
     * {@link PlayerDraftState}, because the card-drafting phase needs to consume
     * them one card at a time.
     *
     * @param player player who placed the totem
     * @param tileCode chosen offer-space code
     */
    private void placeTotemAndRegisterDrawQuota(Player player, String tileCode) {
        this.board.placePlayerOnOfferSpace(player, tileCode);

        int[] drawableCards = this.board.returnNumberDrawableCard(player);
        int upperChoices = drawableCards[0];
        int lowerChoices = drawableCards[1];

        this.playerDraftState.registerDrawQuota(player, upperChoices, lowerChoices);
    }

    /**
     * Moves the round flow after one successful totem placement.
     *
     * <p>If there are still players waiting to place their totems, the method
     * advances the current-player cursor. Disconnected players are skipped in
     * the current phase order: if a skipped player had not placed a totem yet,
     * their totem physically remains on the starting tile. If the cursor reaches
     * the end of the order, the game starts preparing the standard card-drafting
     * phase.
     */
    private void advanceAfterTotemPlacement() {
        if (this.roundFlow.isLastPlayer()) {
            this.startPlayerDrawPhase();
            return;
        }

        this.roundFlow.advanceToNextPlayer();

        while (Boolean.FALSE.equals(this.game.getConnectedPlayers().get(this.roundFlow.getCurrentPlayer()))) {
            if (this.roundFlow.isLastPlayer()) {
                this.startPlayerDrawPhase();
                return;
            }

            this.roundFlow.advanceToNextPlayer();
        }
    }

    /**
     * Prepares the standard card-drafting phase after all totems are placed.
     *<p>
     *     when we are sure that all the active player are on the offer path, and
     *     it's time to change phases, the player still on the starting tile are shifted
     *     as described by the method on the board
     *</p>
     * <p>Offer space A is special: it resolves immediately and the player on A
     * returns to the starting tile before card drafting begins. After resolving
     * A, the board exposes the offer-path resolution order. Only players after a
     * connected ones are copied into the active order for
     * {@link RoundPhase#PLAYER_DRAWS}.
     *
     * <p>This is what allows a player who placed a totem, disconnected, and
     * reconnected before the draft starts to draw normally. A player who is
     * still disconnected when the draft order is created stays physically on
     * the offer path, but does not receive a draft turn, it just gets moved.
     */
    private void startPlayerDrawPhase() {
        this.board.shiftToLastStartingTile();
        this.board.resolveEventA();
        List<Player> draftOrder = this.board.getResolutionOrder();
        int i = 0;
        //Qui stiamo assuemdno che valga l'invariante che almeno un player sia connesso quando arriviamo qua
        //Altgrimenti è una out of bound
        while(Boolean.FALSE.equals(this.game.getConnectedPlayers().get(draftOrder.get(i)))) {
            this.board.movePlayerToLastStartingTile(draftOrder.get(i));
            draftOrder.remove(i);
            i++;
        }
        this.roundFlow.setCurrentPhaseOrder(draftOrder);
        this.roundFlow.setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);
    }


    /**
     * Handles a player disconnection during totem placement.
     *
     * <p>The board position is not changed here. The physical position already
     * tells what happened:
     * <ul>
     *     <li>if the player already placed a totem, they stay on the offer
     *     space;</li>
     *     <li>if the player is current and has not placed a totem, they stay on
     *     the starting tile and the turn advances;</li>
     *     <li>if the player has not reached their turn yet, they stay on the
     *     starting tile and will be skipped only if still disconnected when the
     *     cursor reaches them.</li>
     * </ul>
     *
     * @param player player who disconnected
     */
    public void handlePlayerDisconnection(Player player) {
        if(this.roundFlow.getCurrentPlayerIndex() > this.roundFlow.getCurrentPhaseOrder().indexOf(player)){
            return;
        }else if (this.roundFlow.getCurrentPlayerIndex() == this.roundFlow.getCurrentPhaseOrder().indexOf(player)) {
            this.advanceAfterTotemPlacement();

        }else {
            return;

        }
    }

}
