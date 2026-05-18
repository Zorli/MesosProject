package it.polimi.ingsoft.mesos.server.model.game.game;

import it.polimi.ingsoft.mesos.server.model.game.board.Board;
import it.polimi.ingsoft.mesos.server.model.game.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.Card;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.game.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.game.enums.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.CardNotDrawableException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.CardNotPresentException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.CardRowSelectionNotAllowedException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidGamePhaseException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidGameStatusException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidTurnPhaseException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.NotPlayersTurnException;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

import java.util.List;

/**
 * Executes card-drafting actions of a round.
 *
 * <p>This handler owns the rules behind {@code chooseCard} and
 * {@code extraDrawn}. Standard draws consume the upper-row and lower-row quotas
 * earned from the offer space. The extra draw is a single end-of-round draw
 * granted by one building effect and does not consume those quotas.
 *
 * <p>For standard draws, the handler validates four different concepts:
 * <ul>
 *     <li>the game must be in progress;</li>
 *     <li>the round must be in {@link RoundPhase#PLAYER_DRAWS};</li>
 *     <li>the requesting player must be the current player;</li>
 *     <li>the chosen card must be present, drawable, and compatible with the
 *     player's remaining row quota.</li>
 * </ul>
 *
 * <p>Building affordability is checked through the board drawability rules. A
 * building card is drawable only if the player can pay its effective food cost,
 * including builder discounts. After that validation, this handler pays the
 * cost and adds the building to the player's tribe.
 *
 * <p>Character cards are added to the player's tribe with the overload that
 * receives the player, because some character and building effects react
 * immediately when a character is drawn.
 *
 * <p>When a standard draft turn ends, the player returns from the offer path to
 * the starting tile. That placement contributes to the next round order. If a
 * player disconnects before their draft turn is resolved, this handler closes
 * that draft turn in the same way and returns the player to the starting tile.
 *
 * <p>The extra draw is different: the eligible player is already on the starting
 * tile because the standard draft is over. Drawing the extra card, or
 * disconnecting during the extra-draw turn, simply moves the round to
 * {@link RoundPhase#RESOLVING_EFFECTS}.
 *
 * <p>This class does not resolve lower-row events, reset the board, or compute
 * final scoring. It only moves the round phase to the next high-level step when
 * drafting is over.
 */
public class CardDraftHandler {
    private final Game game;
    private final Board board;
    private final RoundFlow roundFlow;
    private final PlayerDraftState playerDraftState;

    /**
     * Creates the handler used to execute card drafting.
     *
     * @param game game aggregate that owns lifecycle status and state changes
     * @param board board that contains visible cards and offer-path positions
     * @param roundFlow object that knows the current phase and current player
     * @param playerDraftState object that stores remaining standard draw quotas
     */
    public CardDraftHandler(Game game,
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
     * Makes the current player draw one standard card from the board.
     *
     * <p>The method receives a card identifier because clients cannot safely
     * send Java model objects over the network. The board converts the
     * identifier into the actual card object currently visible on the board.
     *
     * @param player player choosing the card
     * @param cardId identifier of the selected card
     */
    public void chooseCard(Player player, int cardId) {
        Card card = this.board.findCardById(cardId);

        this.validateChooseCardRequest(player);
        this.startCardSelectionIfNeeded(player);

        CardRow cardRow = this.validateAndRemoveChosenCard(player, card);
        this.applyChosenCardToPlayer(player, card);
        this.playerDraftState.consumeChoice(player, cardRow);

        if (this.playerDraftState.isTurnCompleted() || !this.hasAnyAvailableDraw(player)) {
            this.completeCurrentPlayerDrawTurn(player);
        }

        this.game.markStateChanged();
    }

    /**
     * Makes the current player draw the single end-of-round extra card.
     *
     * <p>The extra draw is not a standard draft turn. It does not consume upper
     * or lower draw quotas and it does not move the player on the board. After
     * the card has been validated, removed, and applied, the round becomes
     * automatic by switching to {@link RoundPhase#RESOLVING_EFFECTS}. The
     * public {@link Game#extraDrawn(Player, int)} method then asks
     * {@link PassiveRoundPhaseHandler} to continue the passive phases.
     *
     * @param player player choosing the extra card
     * @param cardId identifier of the selected card
     */
    public void extraDrawn(Player player, int cardId) {
        Card card = this.board.findCardById(cardId);

        this.validateExtraDrawRequest(player);
        this.validateAndRemoveExtraDrawCard(player, card);
        this.applyChosenCardToPlayer(player, card);
        this.goToResolvingEffects();
        this.game.markStateChanged();
    }

    /**
     * Checks the game-level preconditions for a standard card draw.
     *
     * <p>This method does not inspect the selected card yet. It only answers
     * the question: "Is this player allowed to perform any standard draw now?".
     * The card-specific validation is done later by
     * {@link #validateAndRemoveChosenCard(Player, Card)}.
     *
     * @param player player asking to draw a standard card
     */
    private void validateChooseCardRequest(Player player) {
        if (this.game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new InvalidGameStatusException("chooseCard", this.game.getStatus());
        }

        if (this.roundFlow.getCurrentRoundPhase() != RoundPhase.PLAYER_DRAWS) {
            throw new InvalidGamePhaseException("chooseCard", this.roundFlow.getCurrentRoundPhase());
        }

        Player currentPlayer = this.roundFlow.getCurrentPlayer();
        if (!currentPlayer.equals(player)) {
            throw new NotPlayersTurnException(currentPlayer, player);
        }
    }

    /**
     * Checks the game-level preconditions for an extra draw.
     *
     * <p>The extra draw can be performed only by the current player during
     * {@link RoundPhase#EXTRA_DRAWS}. The player must also own the building
     * effect that grants the end-of-round extra card.
     *
     * @param player player asking to perform the extra draw
     */
    private void validateExtraDrawRequest(Player player) {
        if (this.game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new InvalidGameStatusException("extraDrawn", this.game.getStatus());
        }

        if (this.roundFlow.getCurrentRoundPhase() != RoundPhase.EXTRA_DRAWS) {
            throw new InvalidGamePhaseException("extraDrawn", this.roundFlow.getCurrentRoundPhase());
        }

        Player currentPlayer = this.roundFlow.getCurrentPlayer();
        if (!currentPlayer.equals(player)) {
            throw new NotPlayersTurnException(currentPlayer, player);
        }

        if (!player.getTribe().hasEndRoundExtraCardBuilding()) {
            throw new IllegalStateException("Player " + player.getNickname() + " cannot perform an extra draw");
        }
    }

    /**
     * Opens the card-selection state for the current player when needed.
     *
     * <p>{@link PlayerDraftState} is normally in
     * {@code TURN_COMPLETED} between players. When the active player chooses the
     * first card of the turn, the state must move to
     * {@code WAITING_FOR_CARD_SELECTION}. If the state is neither completed nor
     * waiting for a card, the command is inconsistent with the expected draft
     * flow and is rejected.
     *
     * @param player player whose standard card selection is starting or continuing
     */
    private void startCardSelectionIfNeeded(Player player) {
        if (this.playerDraftState.isTurnCompleted()) {
            this.playerDraftState.startCardSelection(player);
        }

        if (!this.playerDraftState.isWaitingForCardSelection()) {
            throw new InvalidTurnPhaseException("chooseCard", this.playerDraftState.getPhase());
        }
    }

    /**
     * Validates the chosen card and removes it from the board row that contains it.
     *
     * <p>The method checks three separate rules:
     * the card must currently be visible on the board, the player must be able
     * to draw that specific card, and the player must still have quota for the
     * row containing the card. Only after all checks pass is the card removed
     * from the board.
     *
     * @param player player choosing the card
     * @param card selected card object found by id
     * @return row from which the card was removed
     */
    private CardRow validateAndRemoveChosenCard(Player player, Card card) {
        CardRow cardRow = this.board.getCardRow(card);

        if (cardRow == CardRow.NOT_PRESENT) {
            throw new CardNotPresentException(card);
        }

        if (!this.board.canPlayerDrawCard(player, card)) {
            throw new CardNotDrawableException(player, card);
        }

        if (cardRow == CardRow.UPPER) {
            this.validateUpperRowChoice(player, cardRow);
            this.board.removeUpperCard(card);
            return cardRow;
        }

        this.validateLowerRowChoice(player, cardRow);
        this.board.removeLowerCard(card);
        return cardRow;
    }

    /**
     * Validates an extra-draw card and removes it from the board.
     *
     * <p>The checks are intentionally smaller than the standard draft checks:
     * the card must be present and drawable for the player, but no upper-row or
     * lower-row quota is consumed because the extra draw comes from a building
     * effect.
     *
     * @param player player choosing the card
     * @param card selected card object found by id
     */
    private void validateAndRemoveExtraDrawCard(Player player, Card card) {
        CardRow cardRow = this.board.getCardRow(card);

        if (cardRow == CardRow.NOT_PRESENT) {
            throw new CardNotPresentException(card);
        }

        if (!this.board.canPlayerDrawCard(player, card)) {
            throw new CardNotDrawableException(player, card);
        }

        if (cardRow == CardRow.UPPER) {
            this.board.removeUpperCard(card);
        } else {
            this.board.removeLowerCard(card);
        }
    }

    /**
     * Checks that the player still has an upper-row standard choice.
     *
     * @param player player attempting to draw from the upper row
     * @param cardRow row used only to build the domain exception
     */
    private void validateUpperRowChoice(Player player, CardRow cardRow) {
        if (!this.playerDraftState.canChooseFromUpperRow(player)) {
            throw new CardRowSelectionNotAllowedException(cardRow);
        }
    }

    /**
     * Checks that the player still has a lower-row standard choice.
     *
     * @param player player attempting to draw from the lower row
     * @param cardRow row used only to build the domain exception
     */
    private void validateLowerRowChoice(Player player, CardRow cardRow) {
        if (!this.playerDraftState.canChooseFromLowerRow(player)) {
            throw new CardRowSelectionNotAllowedException(cardRow);
        }
    }

    /**
     * Gives the chosen card to the player and applies the immediate model effect.
     *
     * <p>Building cards must be paid before they enter the player's tribe.
     * Character cards are added through the overload that receives the player,
     * because some character-related effects are triggered at draw time.
     *
     * @param player player receiving the card
     * @param card card removed from the board
     */
    private void applyChosenCardToPlayer(Player player, Card card) {
        if (card instanceof BuildingCard) {
            BuildingCard buildingCard = (BuildingCard) card;
            buildingCard.payEffectiveCost(player);
            player.getTribe().addBuilding(buildingCard);
            return;
        }

        if (card instanceof CharacterCard) {
            CharacterCard characterCard = (CharacterCard) card;
            player.getTribe().addCharacter(characterCard, player);
            return;
        }

        throw new CardNotDrawableException(player, card);
    }

    /**
     * Finishes the current player's standard draw turn.
     *
     * <p>When a player has no useful standard draw left, the player returns from
     * the offer path to the starting tile. This placement on the starting tile
     * contributes to the next round order. The player's consumed quotas are then
     * cleared and the round flow moves to the next drawable player or to the
     * next phase.
     *
     * @param player player whose standard draw turn has ended
     */
    private void completeCurrentPlayerDrawTurn(Player player) {
        this.board.returnPlayerToStartingTile(player);
        this.playerDraftState.resetForNextPlayer(player);
        this.advanceToNextDrawablePlayerOrEndStandardDraft();
    }

    /**
     * Finds the next player who can perform a useful standard draw.
     *
     * <p>A player is skipped when the player has no remaining quota that can be
     * matched with a drawable card currently on the board. Skipped players still
     * return to the starting tile, because their active round participation is
     * over even if they could not choose a card.
     *
     * <p>A disconnected player is handled with the same physical cleanup: their
     * draft turn is considered closed, they return to the starting tile, and the
     * cursor continues looking for the next connected drawable player.
     *
     * <p>If no further player can draw, the method transitions out of the
     * standard card-drafting phase.
     */

    private void advanceToNextDrawablePlayerOrEndStandardDraft() {
        while (true) {
            if (this.roundFlow.isLastPlayer()) {
                this.transitionAfterStandardDraft();
                return;
            }

            this.roundFlow.advanceToNextPlayer();

            Player currentPlayer = this.roundFlow.getCurrentPlayer();

            if (this.game.getConnectedPlayers().get(currentPlayer).equals(Boolean.FALSE)) {
                this.skipDisconnectedPlayer(currentPlayer);
                continue;
            }

            if (!this.hasAnyAvailableDraw(currentPlayer)) {
                this.skipPlayerWithoutUsefulDraw(currentPlayer);
                continue;
            }

            return;
        }
    }


    private void skipPlayerWithoutUsefulDraw(Player player) {
        this.board.returnPlayerToStartingTile(player);
        this.playerDraftState.resetForNextPlayer(player);
    }

    /**
     * Closes the draft turn of a disconnected player.
     *
     * <p>The player may still be physically on an offer space because they
     * placed a totem earlier in the round but did not resolve their draft turn.
     * Returning them to the starting tile keeps the board ready for the next
     * round and removes their remaining standard draw quota.
     *
     * @param player disconnected player to skip
     */
    private void skipDisconnectedPlayer(Player player) {
        this.board.movePlayerToLastStartingTile(player);
        this.playerDraftState.resetForNextPlayer(player);
    }

    /**
     * Chooses the next round phase after all standard draws are complete.
     *
     * <p>Some players may own a building that grants one end-of-round extra
     * card. If at least one eligible player can actually draw a card from the
     * current board, the game enters {@link RoundPhase#EXTRA_DRAWS}. Otherwise
     * no player action is needed and the game moves directly to
     * {@link RoundPhase#RESOLVING_EFFECTS}.
     */
    private void transitionAfterStandardDraft() {
        List<Player> nextRoundOrder = this.board.getNextRoundTurnOrder();
        Player extraDrawPlayer = this.findExtraDrawPlayer(nextRoundOrder);

        if (extraDrawPlayer == null) {
            this.goToResolvingEffects();
            return;
        }

        this.roundFlow.setCurrentPhaseOrder(List.of(extraDrawPlayer));
        this.roundFlow.setCurrentRoundPhase(RoundPhase.EXTRA_DRAWS);
    }

    /**
     * Finds the only player who can perform the end-of-round extra draw.
     *
     * <p>The input order is the next-round order already calculated from the
     * starting tile.
     *
     * @param nextRoundOrder player order taken from the starting tile
     * @return player who should act during {@link RoundPhase#EXTRA_DRAWS}, or
     * {@code null} if nobody can do it
     */
    private Player findExtraDrawPlayer(List<Player> nextRoundOrder) {
        for (Player player : nextRoundOrder) {
            if (Boolean.TRUE.equals(this.game.getConnectedPlayers().get(player))
                    && this.canPerformUsefulExtraDraw(player)) {
                return player;
            }
        }

        return null;
    }

    /**
     * Returns whether an extra draw would be meaningful for the player.
     *
     * <p>The building grants the right to draw, but the board may contain no
     * drawable card for that player. In that case the extra-draw phase is not
     * started for that player.
     *
     * @param player player to inspect
     * @return {@code true} if the player should receive an extra-draw turn
     */
    private boolean canPerformUsefulExtraDraw(Player player) {
        return player.getTribe().hasEndRoundExtraCardBuilding()
                && this.hasAnyDraftableCardOnBoard(player);
    }

    /**
     * Returns whether the player still has any useful standard draw.
     *
     * <p>A remaining quota is useful only if the matching board row contains at
     * least one card that the player can legally draw.
     *
     * @param player player to inspect
     * @return {@code true} if the player can still draw from upper or lower row
     */
    private boolean hasAnyAvailableDraw(Player player) {
        return this.hasAvailableUpperDraw(player) || this.hasAvailableLowerDraw(player);
    }

    /**
     * Returns whether the player can still draw from the upper row.
     *
     * @param player player to inspect
     * @return {@code true} if upper quota and drawable upper cards both exist
     */
    private boolean hasAvailableUpperDraw(Player player) {
        return this.playerDraftState.getRemainingUpperChoices(player) > 0
                && !this.board.getDraftableUpperCards(player).isEmpty();
    }

    /**
     * Returns whether the player can still draw from the lower row.
     *
     * @param player player to inspect
     * @return {@code true} if lower quota and drawable lower cards both exist
     */
    private boolean hasAvailableLowerDraw(Player player) {
        return this.playerDraftState.getRemainingLowerChoices(player) > 0
                && !this.board.getDraftableLowerCards(player).isEmpty();
    }

    /**
     * Returns whether the board has at least one drawable card for the player.
     *
     * <p>This method ignores standard draw quotas. It is used for extra draws,
     * where the right to draw comes from a building effect instead of the
     * upper/lower quota stored in {@link PlayerDraftState}.
     *
     * @param player player to inspect
     * @return {@code true} if any visible board card is drawable by the player
     */
    private boolean hasAnyDraftableCardOnBoard(Player player) {
        return !this.board.getDraftableUpperCards(player).isEmpty()
                || !this.board.getDraftableLowerCards(player).isEmpty();
    }

    /**
     * Handles a disconnection during the standard draft phase.
     *
     * <p>If the disconnected player is the current player, their draft turn is
     * closed immediately and the handler advances to the next connected player
     * who can draw. If the disconnected player is not current, the board is not
     * changed here: the automatic advancement will close their turn only if the
     * cursor later reaches them while they are still disconnected.
     *
     * @param player player who disconnected
     */
    public void handlePlayerDisconnection(Player player) {
            if (this.roundFlow.getCurrentPlayer().equals(player)) {
                this.skipDisconnectedPlayer(player);
                this.advanceToNextDrawablePlayerOrEndStandardDraft();
            }

    }

    /**
     * Handles a disconnection during the single-player extra-draw phase.
     *
     * <p>The extra-draw player is already on the starting tile, so there is no
     * board cleanup to perform. If the disconnected player is the current extra
     * draw player, the optional action is skipped and the round moves to the
     * passive effect-resolution phase.
     *
     * @param player player who disconnected
     */
    public void handleExtraDrawPlayerDisconnection (Player player) {
        if (this.roundFlow.getCurrentPlayer().equals(player)) {
            this.goToResolvingEffects();
        }
    }

    /**
     * Moves the round from drafting to passive effect resolution.
     *
     * <p>The current phase order is restored to the next-round order already
     * present on the starting tile. This gives the passive phases and the reset
     * phase a complete board-based order, while the phase itself becomes
     * {@link RoundPhase#RESOLVING_EFFECTS}. After this method runs,
     * {@link PassiveRoundPhaseHandler#advancePassivePhases()} can continue the
     * automatic part of the round.
     */
    private void goToResolvingEffects() {
        this.roundFlow.setCurrentPhaseOrder(this.board.getNextRoundTurnOrder());
        this.roundFlow.setCurrentRoundPhase(RoundPhase.RESOLVING_EFFECTS);
    }

}
