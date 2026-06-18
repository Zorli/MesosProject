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
        if(! (tileCode == null)) {
            tileCode = tileCode.toUpperCase();
        }
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
            throw new InvalidGameStatusException("moveTotem", this.game.getStatus(), player.getNickname());
        }

        if (this.roundFlow.getCurrentRoundPhase() != RoundPhase.PLACING_TOTEMS) {
            throw new InvalidGamePhaseException("moveTotem", this.roundFlow.getCurrentRoundPhase(), player.getNickname());
        }

        Player currentPlayer = this.roundFlow.getCurrentPlayer();
        if (!currentPlayer.equals(player)) {
            throw new NotPlayersTurnException(currentPlayer, player);
        }

        if (tileCode == null || !this.board.isSpaceAvailable(tileCode)) {
            throw new TileNotAvailableException(tileCode,player.getNickname());
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
     * Prepares the standard card-drafting phase once every totem has been placed.
     *
     * <p>First, the players who never left the starting tile (those who never
     * placed a totem, typically because they were disconnected when their turn
     * came) are shifted to the bottom of the next-round order through
     * {@link Board#shiftToLastStartingTile()}, freeing the first starting blocks
     * for the players that will return from the offer path.
     *
     * <p>Offer space A is special: {@link Board#resolveEventA()} resolves it
     * immediately and returns the player who was on A to the starting tile,
     * before card drafting begins. The board then exposes the offer-path
     * resolution order, which contains every player still standing on an offer
     * space (in resolution order).
     *
     * <p>That resolution order is filtered by connection state to build the
     * active draft order:
     * <ul>
     *     <li>a <em>connected</em> player is kept and will receive a draft turn
     *     during {@link RoundPhase#PLAYER_DRAWS};</li>
     *     <li>a <em>disconnected</em> player is removed from the order and moved
     *     off the offer path to the last starting block via
     *     {@link Board#movePlayerToLastStartingTile(Player)}, so they get no
     *     draft turn and start the next round in the worst position.</li>
     * </ul>
     * This is what lets a player who placed a totem, disconnected, and
     * reconnected before drafting starts draw normally: by the time the order is
     * built they are connected again and are kept.
     *
     * <p>If the filtering empties the order (no connected player is left on the
     * offer path), there is no one to draft, so the round skips straight to
     * {@link RoundPhase#RESETTING_BOARD}.
     */
    private void startPlayerDrawPhase() {
        this.board.shiftToLastStartingTile();
        this.board.resolveEventA();
        List<Player> draftOrder = this.board.getResolutionOrder();
        int i = 0;
        // Invariant: at least one connected player must exist at this point, otherwise draftOrder.get(i) throws.
        while(!draftOrder.isEmpty() && Boolean.FALSE.equals(this.game.getConnectedPlayers().get(draftOrder.get(i)))) {
            this.board.movePlayerToLastStartingTile(draftOrder.get(i));
            draftOrder.remove(i);
        }

        if (draftOrder.isEmpty()) {
            this.roundFlow.setCurrentRoundPhase(RoundPhase.RESETTING_BOARD);
            this.game.markStateChanged();
            return;
        }
        this.roundFlow.setCurrentPhaseOrder(draftOrder);
        this.roundFlow.setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);
        this.game.skipInitialDraftPlayerIfNeeded();
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
