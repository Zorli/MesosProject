package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.game.board.Board;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.Card;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.game.GameActionExecutor;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ServerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link GameActionExecutor}.
 *
 * <p>The executor is the common wrapper used by public game actions. These
 * tests verify its observable contract using a tiny {@link Game} double:
 * changed actions emit one payload, unchanged actions emit nothing, and invalid
 * actions emit one error.
 */
class GameActionExecutorTest {

    /**
     * If an action does not change the game version, listeners do not need a new
     * game payload.
     */
    @Test
    void unchangedActionDoesNotNotifyPayload() {
        RecordingGame game = new RecordingGame();
        GameActionExecutor executor = new GameActionExecutor(game);

        executor.execute(() -> {
        });

        assertEquals(List.of(), game.sentPayloads);
        assertEquals(List.of(), game.sentErrors);
    }

    /**
     * If an action changes the state version, the executor builds and sends one
     * final payload after the action completes.
     */
    @Test
    void changedActionNotifiesOnePayload() {
        RecordingGame game = new RecordingGame();
        GameActionExecutor executor = new GameActionExecutor(game);

        executor.execute(game::changeVersion);

        assertEquals(List.of(game.gamePayload), game.sentPayloads);
        assertEquals(List.of(), game.sentErrors);
    }

    /**
     * Multiple internal version increments in the same public action still
     * produce a single final payload. The executor only compares before and
     * after the wrapped action.
     */
    @Test
    void actionWithMultipleVersionChangesStillNotifiesOnce() {
        RecordingGame game = new RecordingGame();
        GameActionExecutor executor = new GameActionExecutor(game);

        executor.execute(() -> {
            game.changeVersion();
            game.changeVersion();
        });

        assertEquals(List.of(game.gamePayload), game.sentPayloads);
    }

    /**
     * Validation failures are converted into error notifications instead of
     * escaping from the public action wrapper.
     */
    @Test
    void illegalArgumentIsReportedAsError() {
        RecordingGame game = new RecordingGame();
        GameActionExecutor executor = new GameActionExecutor(game);

        executor.execute(() -> {
            throw new IllegalArgumentException("bad action");
        });

        assertEquals(List.of(), game.sentPayloads);
        assertEquals(List.of("IllegalArgumentException:bad action"), game.sentErrors);
    }

    /**
     * Invalid state failures follow the same error-notification path as invalid
     * arguments.
     */
    @Test
    void illegalStateIsReportedAsError() {
        RecordingGame game = new RecordingGame();
        GameActionExecutor executor = new GameActionExecutor(game);

        executor.execute(() -> {
            throw new IllegalStateException("wrong phase");
        });

        assertEquals(List.of(), game.sentPayloads);
        assertEquals(List.of("IllegalStateException:wrong phase"), game.sentErrors);
    }

    /**
     * Minimal game double exposing only the hooks used by the executor.
     */
    private static class RecordingGame extends Game {
        private long version;
        private final ServerPayload gamePayload = new ErrorPayload("TEST", "game-payload");
        private final List<ServerPayload> sentPayloads = new ArrayList<>();
        private final List<String> sentErrors = new ArrayList<>();

        RecordingGame() {
            super(1, List.of(new Player("Alice", 0, 0, TotemColor.RED)), new Board(
                    TribeDeck.fromOrderedCards(new ArrayList<Card>()),
                    BuildingDeck.fromOrderedCards(new ArrayList<Card>()),
                    List.of(new Player("Alice", 0, 0, TotemColor.RED))
            ));
        }

        void changeVersion() {
            this.version++;
        }

        @Override
        public long getStateVersion() {
            return this.version;
        }

        @Override
        public ServerPayload buildPayload() {
            return this.gamePayload;
        }

        @Override
        public void notifyListeners(ServerPayload payload) {
            this.sentPayloads.add(payload);
        }

        @Override
        public void notifyError(String code, String message) {
            this.sentErrors.add(code + ":" + message);
        }
    }
}
