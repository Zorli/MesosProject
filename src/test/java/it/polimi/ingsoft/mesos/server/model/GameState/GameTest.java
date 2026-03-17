package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTest {

    @Test
    void setupInitializesBoardTurnOrderAndActionResult() {
        ArrayList<Player> players = players("Alice", "Bob");
        RecordingBoard board = new RecordingBoard(players);
        TurnState turnState = new TurnState();
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack();
        Game game = new Game(42, players, turnState, turnOrderTrack, board, null, null);

        ActionResult actionResult = game.setup();

        assertEquals(List.of("buildOfferPath", "setupInitialRows"), board.calls);
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertEquals(TurnPhase.WAITING_FOR_TOTEM_MOVE, actionResult.getTurnPhase());
        assertEquals("Alice", actionResult.getExpectedPlayer());
        assertEquals(GameStatus.IN_PROGRESS, actionResult.getGameStatus());
        assertEquals(Era.first, actionResult.getCurrentEra());
        assertEquals(1, actionResult.getRoundNumber());
        assertFalse(actionResult.isTurnEnded());
        assertFalse(actionResult.isRoundEnded());
        assertFalse(actionResult.isGameEnded());
        assertTrue(actionResult.getUpperRowCards().isEmpty());
        assertTrue(actionResult.getLowerRowCards().isEmpty());
        assertTrue(actionResult.getDrawableUpperCards().isEmpty());
        assertTrue(actionResult.getDrawableLowerCards().isEmpty());
    }

    @Test
    void setupKeepsCurrentEraAlreadyConfiguredOnGame() {
        ArrayList<Player> players = players("Alice", "Bob");
        RecordingBoard board = new RecordingBoard(players);
        Game game = new Game(42, players, new TurnState(), new TurnOrderTrack(), board, null, null);
        game.setCurrentEra(Era.second);

        ActionResult actionResult = game.setup();

        assertEquals(Era.second, game.getCurrentEra());
        assertEquals(Era.second, actionResult.getCurrentEra());
    }

    @Test
    void setupPropagatesBuildOfferPathFailureWithoutAdvancingGameState() {
        ArrayList<Player> players = players("Alice", "Bob");
        RecordingBoard board = new RecordingBoard(players);
        board.failOnBuildOfferPath = true;
        Game game = new Game(42, players, new TurnState(), new TurnOrderTrack(), board, null, null);

        IllegalStateException exception = assertThrows(IllegalStateException.class, game::setup);

        assertEquals("Offer path failure", exception.getMessage());
        assertEquals(List.of("buildOfferPath"), board.calls);
        assertEquals(GameStatus.SETUP, game.getStatus());
    }

    @Test
    void setupPropagatesInitialRowFailureWithoutUpdatingTurnOrderOrStatus() {
        ArrayList<Player> players = players("Alice", "Bob");
        RecordingBoard board = new RecordingBoard(players);
        board.failOnSetupInitialRows = true;
        Game game = new Game(42, players, new TurnState(), new TurnOrderTrack(), board, null, null);

        IllegalStateException exception = assertThrows(IllegalStateException.class, game::setup);

        assertEquals("Initial rows failure", exception.getMessage());
        assertEquals(List.of("buildOfferPath", "setupInitialRows"), board.calls);
        assertEquals(GameStatus.SETUP, game.getStatus());
    }

    @Test
    void setupFailsWhenTurnOrderCannotBeInitialized() {
        ArrayList<Player> players = new ArrayList<>();
        RecordingBoard board = new RecordingBoard(players);
        Game game = new Game(42, players, new TurnState(), new TurnOrderTrack(), board, null, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, game::setup);

        assertEquals("Turn order cannot be null or empty", exception.getMessage());
        assertEquals(List.of("buildOfferPath", "setupInitialRows"), board.calls);
        assertEquals(GameStatus.SETUP, game.getStatus());
    }

    @Test
    void moveTotemThrowsCustomExceptionWhenWrongPlayerActs() {
        ArrayList<Player> players = players("Alice", "Bob");
        RecordingBoard board = new RecordingBoard(players);
        Game game = new Game(42, players, new TurnState(), new TurnOrderTrack(), board, null, null);
        game.setup();

        NotPlayersTurnException exception = assertThrows(
                NotPlayersTurnException.class,
                () -> game.moveTotem(players.get(1), "A")
        );

        assertEquals("It is not the turn of player Bob. Current player: Alice", exception.getMessage());
    }

    @Test
    void moveTotemThrowsInvalidTurnPhaseExceptionWhenTotemWasAlreadyMoved() {
        ArrayList<Player> players = players("Alice", "Bob");
        RecordingBoard board = new RecordingBoard(players);
        Game game = new Game(42, players, new TurnState(), new TurnOrderTrack(), board, null, null);
        game.setup();
        game.moveTotem(players.get(0), "A");
        game.moveTotem(players.get(1), "B");

        InvalidGamePhaseException exception = assertThrows(
                InvalidGamePhaseException.class,
                () -> game.moveTotem(players.get(0), "C")
        );

        assertEquals(
                "Action moveTotem is not allowed during round phase PLAYER_TURNS",
                exception.getMessage()
        );
    }

    @Test
    void moveTotemOnLastPlayerResolvesTileAAndMaySkipFirstResolutionPlayer() {
        ArrayList<Player> players = players("Alice", "Bob");
        RecordingBoard board = new RecordingBoard(players);
        board.resolveEventAReturnValue = true;
        Game game = new Game(42, players, new TurnState(), new TurnOrderTrack(), board, null, null);
        game.setup();

        game.moveTotem(players.get(0), "A");
        ActionResult actionResult = game.moveTotem(players.get(1), "B");

        assertTrue(board.resolveEventACalled);
        assertEquals(TurnPhase.WAITING_FOR_CARD_SELECTION, actionResult.getTurnPhase());
        assertEquals("Bob", actionResult.getExpectedPlayer());
        assertEquals(2, actionResult.getRoundNumber());
    }

    private static ArrayList<Player> players(String... nicknames) {
        ArrayList<Player> players = new ArrayList<>();
        TotemColor[] colors = TotemColor.values();

        for (int i = 0; i < nicknames.length; i++) {
            players.add(new Player(nicknames[i], 0, 0, colors[i], null));
        }

        return players;
    }

    private static final class RecordingBoard extends Board {
        private final List<String> calls = new ArrayList<>();
        private final List<Player> players;
        private final Set<String> occupiedSpaces = new HashSet<>();
        private boolean failOnBuildOfferPath;
        private boolean failOnSetupInitialRows;
        private boolean resolveEventAReturnValue;
        private boolean resolveEventACalled;

        private RecordingBoard(List<Player> players) {
            super(null, null, players);
            this.players = new ArrayList<>(players);
        }

        @Override
        public void buildOfferPath() {
            this.calls.add("buildOfferPath");
            if (this.failOnBuildOfferPath) {
                throw new IllegalStateException("Offer path failure");
            }
        }

        @Override
        public void setupInitialRows() {
            this.calls.add("setupInitialRows");
            if (this.failOnSetupInitialRows) {
                throw new IllegalStateException("Initial rows failure");
            }
        }

        @Override
        public boolean isSpaceAvailable(String targetCode) {
            return List.of("A", "B", "C").contains(targetCode) && !this.occupiedSpaces.contains(targetCode);
        }

        @Override
        public void placePlayerOnOfferSpace(Player player, String targetCode) {
            this.occupiedSpaces.add(targetCode);
        }

        @Override
        public List<Player> getResolutionOrder() {
            return new ArrayList<>(this.players);
        }

        @Override
        public boolean resolveEventA() {
            this.resolveEventACalled = true;
            return this.resolveEventAReturnValue;
        }

        @Override
        public int[] returnNumberDrawableCard(Player player) {
            return new int[]{1, 0};
        }
    }
}
