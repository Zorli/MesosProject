package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.player.Player;

/**
 * Signals that a card is present on the board but cannot currently be drawn by the player.
 */
public class CardNotDrawableException extends IllegalStateException {
    /**
     * Creates the exception for a player-card pair that violates drawability rules.
     *
     * @param player player who attempted the draw
     * @param card card that could not be drawn
     */
    public CardNotDrawableException(Player player, Card card) {
        super(buildMessage(player, card));
    }

    /**
     * Builds the diagnostic message associated with the invalid draw attempt.
     *
     * @param player player who attempted the draw
     * @param card card that could not be drawn
     * @return formatted exception message
     */
    private static String buildMessage(Player player, Card card) {
        String playerNickname = player == null ? "unknown" : player.getNickname();
        String cardId = card == null ? "null" : String.valueOf(card.getId());
        return "Card " + cardId + " is not drawable by player " + playerNickname;
    }
}
