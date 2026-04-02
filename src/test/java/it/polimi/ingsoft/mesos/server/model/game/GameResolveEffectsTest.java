package it.polimi.ingsoft.mesos.server.model.game;

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
 * Focused tests for lower-event resolution and phase transitions in {@link Game}.
 */
class GameResolveEffectsTest {

    @Test
    void resolveEffectsDelegatesToBoardAndMovesToResettingBoard() {
        Player alice = player("Alice", TotemColor.RED);
        RecordingBoard board = new RecordingBoard();
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.RESOLVING_EFFECTS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        game.resolveEffects();

        assertTrue(board.resolveLowerEventsCalled);
        assertEquals(RoundPhase.RESETTING_BOARD, game.getTurnOrderTrack().getCurrentRoundPhase());
    }

    @Test
    void resolveEffectsRejectsWrongRoundPhase() {
        Player alice = player("Alice", TotemColor.RED);
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS),
                new RecordingBoard(),
                Era.first,
                GameStatus.IN_PROGRESS
        );

        assertThrows(InvalidGamePhaseException.class, game::resolveEffects);
    }

    @Test
    void resolveEffectsMovesToFinalScoringOnRoundTen() {
        Player alice = player("Alice", TotemColor.RED);
        RecordingBoard board = new RecordingBoard();
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 10, RoundPhase.RESOLVING_EFFECTS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        game.resolveEffects();

        assertTrue(board.resolveLowerEventsCalled);
        assertEquals(RoundPhase.FINAL_SCORING, game.getTurnOrderTrack().getCurrentRoundPhase());
    }

    private static Player player(String nickname, TotemColor color) {
        Player player = new Player(nickname, 0, 0, color);
        return player;
    }

    /**
     * Minimal board double exposing only lower-event resolution to the game aggregate.
     */
    private static final class RecordingBoard extends Board {
        private boolean resolveLowerEventsCalled;

        private RecordingBoard() {
            super(null, null, new ArrayList<>());
        }

        @Override
        public void resolveLowerEvents() {
            this.resolveLowerEventsCalled = true;
        }
    }
}
