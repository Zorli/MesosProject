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
import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidGamePhaseException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidGameStatusException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.NotPlayersTurnException;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

/**
 * Executes an end-of-round extra card draw.
 *
 * <p>Extra draws happen after all standard card drafting is complete. Only
 * players who own a building with the {@code END_ROUND_EXTRA_CARD} benefit are
 * allowed to act during this phase.
 *
 * <p>An extra draw is different from a standard draw:
 * <ul>
 *     <li>it does not consume upper-row or lower-row quotas;</li>
 *     <li>it does not use {@link PlayerDraftState};</li>
 *     <li>it is granted by a building effect, not by the offer space selected
 *     during totem placement.</li>
 * </ul>
 *
 * <p>The class validates the phase, the current player, the player's
 * eligibility, the card presence, and the card drawability. Building cards are
 * still checked through their normal drawability rule, so the player must be
 * able to pay the effective food cost before receiving the building.
 */
public class ExtraCardDraftHandler {
    private final Game game;
    private final Board board;
    private final RoundFlow roundFlow;

    /**
     * Creates the handler used to execute extra card draws.
     *
     * @param game game aggregate that owns lifecycle status and state changes
     * @param board board that contains visible cards and next-round order
     * @param roundFlow object that knows the current phase and current player
     */
    public ExtraCardDraftHandler(Game game, Board board, RoundFlow roundFlow) {
        if (game == null) {
            throw new IllegalArgumentException("Game cannot be null");
        }
        if (board == null) {
            throw new IllegalArgumentException("Board cannot be null");
        }
        if (roundFlow == null) {
            throw new IllegalArgumentException("Round flow cannot be null");
        }

        this.game = game;
        this.board = board;
        this.roundFlow = roundFlow;
    }

    /**
     * Makes the current player draw one extra card.
     *
     * <p>The player must already be part of the extra-draw order prepared by the
     * standard draft handler. This method still checks the owned building,
     * because the model must reject invalid direct calls as well.
     *
     * @param player player performing the extra draw
     * @param cardId identifier of the selected card
     */
    public void extraDrawn(Player player, int cardId) {
        Card card = this.board.findCardById(cardId);

        this.validateExtraDrawRequest(player);
        this.validateAndRemoveExtraDrawCard(player, card);
        this.applyChosenCardToPlayer(player, card);
        this.advanceAfterExtraDraw();

        this.game.markStateChanged();
    }

    /**
     * Checks whether the player can perform an extra draw right now.
     *
     * <p>The validation is independent from the selected card. It verifies that
     * the game is running, the round is in the extra-draw phase, the requester
     * is the current player in the extra-draw order, and the player owns the
     * building effect that grants the extra card.
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
     * Validates the selected extra card and removes it from the board.
     *
     * <p>Extra draws do not consume standard upper/lower quotas, so this method
     * checks only board presence and card drawability. After validation, the card
     * is removed from the row that currently contains it.
     *
     * @param player player performing the extra draw
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
     * Gives the extra-drawn card to the player.
     *
     * <p>The effect is the same as a standard draw: buildings are paid and added
     * to the tribe, while characters are added through the method that can
     * trigger immediate character-related effects.
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
     * Advances the round flow after one extra draw.
     *
     * <p>If another eligible player is still present in the extra-draw order,
     * the cursor moves to that player. If the current player was the last one,
     * the game reinstates the full next-round order and enters the automatic
     * effect-resolution phase.
     */
    private void advanceAfterExtraDraw() {
        if (this.roundFlow.isLastPlayer()) {
            this.roundFlow.setCurrentPhaseOrder(this.board.getNextRoundTurnOrder());
            this.roundFlow.setCurrentRoundPhase(RoundPhase.RESOLVING_EFFECTS);
            return;
        }

        this.roundFlow.advanceToNextPlayer();
    }
}
