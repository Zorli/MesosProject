package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

        assertGameError(game, InvalidGamePhaseException.class, game::resolveEffects);
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

    private static void assertGameError(Game game, Class<? extends RuntimeException> expectedError, Runnable action) {
        RecordingListener listener = new RecordingListener();
        long previousVersion = game.getStateVersion();
        game.addListener(listener);

        action.run();

        assertEquals(previousVersion, game.getStateVersion());
        ErrorPayload errorPayload = assertInstanceOf(ErrorPayload.class, listener.payload);
        assertEquals(expectedError.getSimpleName(), errorPayload.code);
    }

    private static final class RecordingListener implements Listener {
        private Object payload;

        @Override
        public void notifyUpdate(Object payload) {
            this.payload = payload;
        }
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
