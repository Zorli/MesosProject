package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.controller.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Focused tests for the totem-placement branch of the {@link Game} round flow.
 */
class GameMoveTotemTest {

    @Test
    void moveTotemStoresDrawableCardsForPlayerAndAdvancesPlacementOrder() {
        ArrayList<Player> players = players("Alice", "Bob");
        RecordingBoard board = new RecordingBoard(players);
        TurnState turnState = new TurnState();
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack();
        Game game = new Game(1, players, turnState, turnOrderTrack, board);
        game.setup();

        game.moveTotem(players.get(0), "A");

        assertEquals(1, turnState.getRemainingUpperChoices(players.get(0)));
        assertEquals(2, turnState.getRemainingLowerChoices(players.get(0)));
        assertEquals(players.get(1), turnOrderTrack.getCurrentPlayer());
        assertEquals(RoundPhase.PLACING_TOTEMS, turnOrderTrack.getCurrentRoundPhase());
    }

    @Test
    void moveTotemOnLastPlacementSwitchesRoundPhaseToPlayerDrawsAndUsesResolutionOrder() {
        ArrayList<Player> players = players("Alice", "Bob");
        RecordingBoard board = new RecordingBoard(players);
        board.resolutionOrder = List.of(players.get(1), players.get(0));
        TurnState turnState = new TurnState();
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack();
        Game game = new Game(1, players, turnState, turnOrderTrack, board);
        game.setup();

        game.moveTotem(players.get(0), "A");
        game.moveTotem(players.get(1), "B");

        assertEquals(RoundPhase.PLAYER_DRAWS, turnOrderTrack.getCurrentRoundPhase());
        assertEquals(players.get(1), turnOrderTrack.getCurrentPlayer());
        assertEquals(1, turnState.getRemainingUpperChoices(players.get(0)));
        assertEquals(2, turnState.getRemainingLowerChoices(players.get(0)));
        assertEquals(1, turnState.getRemainingUpperChoices(players.get(1)));
        assertEquals(2, turnState.getRemainingLowerChoices(players.get(1)));
    }

    @Test
    void moveTotemOnLastPlacementResolvesTileAThenGenericSkipSelectsNextDrawablePlayer() {
        ArrayList<Player> players = players("Alice", "Bob", "Carol");
        RecordingBoard board = new RecordingBoard(players);
        board.resolutionOrder = List.of(players.get(0), players.get(2), players.get(1));
        board.drawableCardsByPlayer.put(players.get(0), new int[]{0, 0});
        board.drawableCardsByPlayer.put(players.get(2), new int[]{1, 2});
        board.drawableCardsByPlayer.put(players.get(1), new int[]{1, 2});
        TurnState turnState = new TurnState();
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack();
        Game game = new Game(1, players, turnState, turnOrderTrack, board);
        game.setup();

        game.moveTotem(players.get(0), "A");
        game.moveTotem(players.get(1), "B");
        game.moveTotem(players.get(2), "C");

        assertEquals(RoundPhase.PLAYER_DRAWS, turnOrderTrack.getCurrentRoundPhase());
        assertEquals(players.get(2), turnOrderTrack.getCurrentPlayer());
    }

    @Test
    void moveTotemSkipsLeadingPlayersWithoutDrawableCardsInResolutionOrder() {
        ArrayList<Player> players = players("Alice", "Bob", "Carol");
        RecordingBoard board = new RecordingBoard(players);
        board.resolutionOrder = List.of(players.get(0), players.get(1), players.get(2));
        board.drawableCardsByPlayer.put(players.get(0), new int[]{0, 0});
        board.drawableCardsByPlayer.put(players.get(1), new int[]{0, 0});
        board.drawableCardsByPlayer.put(players.get(2), new int[]{1, 1});
        TurnState turnState = new TurnState();
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack();
        Game game = new Game(1, players, turnState, turnOrderTrack, board);
        game.setup();

        game.moveTotem(players.get(0), "A");
        game.moveTotem(players.get(1), "B");
        game.moveTotem(players.get(2), "C");

        assertEquals(RoundPhase.PLAYER_DRAWS, turnOrderTrack.getCurrentRoundPhase());
        assertEquals(players.get(2), turnOrderTrack.getCurrentPlayer());
    }

    @Test
    void moveTotemRejectsWrongPlayer() {
        ArrayList<Player> players = players("Alice", "Bob");
        RecordingBoard board = new RecordingBoard(players);
        Game game = new Game(1, players, new TurnState(), new TurnOrderTrack(), board);
        game.setup();

        assertThrows(NotPlayersTurnException.class, () -> game.moveTotem(players.get(1), "A"));
    }

    @Test
    void moveTotemRejectsUnavailableTile() {
        ArrayList<Player> players = players("Alice", "Bob");
        RecordingBoard board = new RecordingBoard(players);
        Game game = new Game(1, players, new TurnState(), new TurnOrderTrack(), board);
        game.setup();

        assertThrows(TileNotAvailableException.class, () -> game.moveTotem(players.get(0), "Z"));
    }

    @Test
    void moveTotemRejectsCallOutsidePlacementPhase() {
        ArrayList<Player> players = players("Alice", "Bob");
        RecordingBoard board = new RecordingBoard(players);
        Game game = new Game(1, players, new TurnState(), new TurnOrderTrack(), board);
        game.setup();
        game.moveTotem(players.get(0), "A");
        game.moveTotem(players.get(1), "B");

        assertThrows(InvalidGamePhaseException.class, () -> game.moveTotem(players.get(1), "C"));
    }

    private static ArrayList<Player> players(String... nicknames) {
        ArrayList<Player> players = new ArrayList<>();
        TotemColor[] colors = TotemColor.values();

        for (int i = 0; i < nicknames.length; i++) {
            players.add(new Player(nicknames[i], 0, 0, colors[i], null));
        }

        return players;
    }

    /**
     * Minimal board double exposing placement, availability, and resolution-order behavior.
     */
    private static final class RecordingBoard extends Board {
        private final Set<String> occupiedSpaces = new HashSet<>();
        private List<Player> resolutionOrder;
        private final java.util.Map<Player, int[]> drawableCardsByPlayer = new java.util.HashMap<>();

        private RecordingBoard(List<Player> players) {
            super(null, null, players);
            this.resolutionOrder = new ArrayList<>(players);
        }

        @Override
        public void buildOfferPath() {
        }

        @Override
        public void setupInitialRows() {
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
        public int[] returnNumberDrawableCard(Player player) {
            return this.drawableCardsByPlayer.getOrDefault(player, new int[]{1, 2});
        }

        @Override
        public List<Player> getResolutionOrder() {
            return new ArrayList<>(this.resolutionOrder);
        }

        @Override
        public void resolveEventA() {}
    }
}
