package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.player.Player;

/**
 * Signals that an action was attempted by a player who is not the current player.
 */
public class NotPlayersTurnException extends IllegalStateException {

    public NotPlayersTurnException(Player expectedPlayer, Player actualPlayer) {
        super(buildMessage(expectedPlayer, actualPlayer));
    }

    private static String buildMessage(Player expectedPlayer, Player actualPlayer) {
        String expectedNickname = expectedPlayer == null ? "unknown" : expectedPlayer.getNickname();
        String actualNickname = actualPlayer == null ? "unknown" : actualPlayer.getNickname();
        return "It is not the turn of player " + actualNickname + ". Current player: " + expectedNickname;
    }
}
