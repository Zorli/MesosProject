package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for board-reset and era-transition behavior in {@link Game}.
 */
class GameResetBoardTest {

    @Test
    void resetBoardResetsBoardAdvancesRoundAndKeepsExistingOrder() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        RecordingBoard board = new RecordingBoard();
        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(alice, 1, 0);
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(bob, alice), 0, 1, RoundPhase.RESETTING_BOARD);
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice, bob)),
                turnState,
                turnOrderTrack,
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        game.resetBoard();

        assertTrue(board.resetBoardAfterRoundCalled);
        assertEquals(Era.first, board.resetBoardAfterRoundInputEra);
        assertEquals(Era.first, game.getCurrentEra());
        assertEquals(2, game.getTurnOrderTrack().getRoundNumber());
        assertEquals(RoundPhase.PLACING_TOTEMS, game.getTurnOrderTrack().getCurrentRoundPhase());
        assertEquals(List.of(bob, alice), game.getTurnOrderTrack().getTurnOrder());
        assertEquals(0, game.getTurnState().getRemainingUpperChoices(alice));
    }

    @Test
    void resetBoardMovesToNextEraWhenBoardSignalsEraChange() {
        Player alice = player("Alice", TotemColor.RED);
        RecordingBoard board = new RecordingBoard();
        board.eraChanged = true;
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 4, RoundPhase.RESETTING_BOARD),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        game.resetBoard();

        assertEquals(Era.second, game.getCurrentEra());
        assertEquals(5, game.getTurnOrderTrack().getRoundNumber());
        assertEquals(RoundPhase.PLACING_TOTEMS, game.getTurnOrderTrack().getCurrentRoundPhase());
    }

    @Test
    void resetBoardRejectsWrongRoundPhase() {
        Player alice = player("Alice", TotemColor.RED);
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.RESOLVING_EFFECTS),
                new RecordingBoard(),
                Era.first,
                GameStatus.IN_PROGRESS
        );

        assertThrows(InvalidGamePhaseException.class, game::resetBoard);
    }

    private static Player player(String nickname, TotemColor color) {
        Player player = new Player(nickname, 0, 0, color);
        return player;
    }

    /**
     * Minimal board double exposing only round-reset behavior to the game aggregate.
     */
    private static final class RecordingBoard extends Board {
        private boolean resetBoardAfterRoundCalled;
        private boolean eraChanged;
        private Era resetBoardAfterRoundInputEra;

        private RecordingBoard() {
            super(null, null, new ArrayList<>());
        }

        @Override
        public boolean resetBoardAfterRound(Era currentEra) {
            this.resetBoardAfterRoundCalled = true;
            this.resetBoardAfterRoundInputEra = currentEra;
            return this.eraChanged;
        }
    }
}
