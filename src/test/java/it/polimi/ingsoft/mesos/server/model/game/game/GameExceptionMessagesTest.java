package it.polimi.ingsoft.mesos.server.model.game.game;

import it.polimi.ingsoft.mesos.server.model.game.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Hunter;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.game.game.enums.DraftTurnState;
import it.polimi.ingsoft.mesos.server.model.game.game.enums.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.CardNotDrawableException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.CardNotPresentException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.CardRowSelectionNotAllowedException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidConnectionState;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidGamePhaseException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidGameStatusException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidTurnPhaseException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.NotPlayersTurnException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.TileNotAvailableException;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for game-domain exception messages.
 *
 * <p>Public game actions convert exception class names and messages into error
 * payloads. These messages are therefore part of the observable client-facing
 * contract.
 */
class GameExceptionMessagesTest {

    /**
     * Card-related exceptions must include the rejected card and player context
     * that the client can show to the user.
     */
    @Test
    void cardErrorsDescribeTheRejectedCardRequest() {
        Player alice = player("Alice");
        Hunter hunter = new Hunter(7, Era.first, false, TribeCardType.HUNTER);

        assertEquals("Requested card is not present on the board: 7", new CardNotPresentException(hunter).getMessage());
        assertEquals("Requested card is not present on the board: null", new CardNotPresentException(null).getMessage());
        assertEquals("Card 7 is not drawable by player Alice", new CardNotDrawableException(alice, hunter).getMessage());
        assertEquals("Card null is not drawable by player unknown", new CardNotDrawableException(null, null).getMessage());
        assertEquals("Card selection is not allowed from row: LOWER", new CardRowSelectionNotAllowedException(CardRow.LOWER).getMessage());
    }

    /**
     * Phase and status exceptions identify the refused action and the current
     * game state that made it invalid.
     */
    @Test
    void phaseAndStatusErrorsDescribeTheInvalidActionContext() {
        assertEquals(
                "Action setup is not allowed when game status is IN_PROGRESS",
                new InvalidGameStatusException("setup", GameStatus.IN_PROGRESS).getMessage()
        );
        assertEquals(
                "Action moveTotem is not allowed during round phase PLAYER_DRAWS",
                new InvalidGamePhaseException("moveTotem", RoundPhase.PLAYER_DRAWS).getMessage()
        );
        assertEquals(
                "Action chooseCard is not allowed during phase TURN_COMPLETED",
                new InvalidTurnPhaseException("chooseCard", DraftTurnState.TURN_COMPLETED).getMessage()
        );
    }

    /**
     * Turn, tile, and connection errors expose stable messages for invalid
     * command feedback.
     */
    @Test
    void turnTileAndConnectionErrorsDescribeTheRejectedAction() {
        Player alice = player("Alice");
        Player bob = player("Bob");

        assertEquals(
                "It is not the turn of player Bob. Current player: Alice",
                new NotPlayersTurnException(alice, bob).getMessage()
        );
        assertEquals(
                "It is not the turn of player unknown. Current player: unknown",
                new NotPlayersTurnException(null, null).getMessage()
        );
        assertEquals("Selected tile is not available: B", new TileNotAvailableException("B").getMessage());
        assertEquals("Selected tile is not available: 3", new TileNotAvailableException(3).getMessage());
        assertEquals("Invalid connection state: only one connected", new InvalidConnectionState("only one connected").getMessage());
    }

    private static Player player(String nickname) {
        return new Player(nickname, 0, 0, TotemColor.RED);
    }
}
