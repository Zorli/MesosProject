package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.controller.player.Player;

/**
 * Signals that an action was attempted by a player who is not the current player.
 */
public class NotPlayersTurnException extends IllegalStateException {

    /**
     * Creates the exception describing the expected and actual player.
     *
     * @param expectedPlayer player whose turn it currently is
     * @param actualPlayer player who attempted the action
     */
    public NotPlayersTurnException(Player expectedPlayer, Player actualPlayer) {
        super(buildMessage(expectedPlayer, actualPlayer));
    }

    /**
     * Formats the exception message shown when the wrong player attempts an action.
     *
     * @param expectedPlayer player whose turn it currently is
     * @param actualPlayer player who attempted the action
     * @return human-readable error message
     */
    private static String buildMessage(Player expectedPlayer, Player actualPlayer) {
        String expectedNickname = expectedPlayer == null ? "unknown" : expectedPlayer.getNickname();
        String actualNickname = actualPlayer == null ? "unknown" : actualPlayer.getNickname();
        return "It is not the turn of player " + actualNickname + ". Current player: " + expectedNickname;
    }
}
