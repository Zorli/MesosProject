package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static it.polimi.ingsoft.mesos.server.model.game.GameTestSupport.assertGameError;
import static it.polimi.ingsoft.mesos.server.model.game.GameTestSupport.player;
import static it.polimi.ingsoft.mesos.server.model.game.GameTestSupport.players;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLifecycleTest {

    @Test
    void constructorKeepsImmutablePlayersAndExposesRestoredSnapshotState() {
        ArrayList<Player> players = new ArrayList<>(List.of(
                player("Alice", 0, TotemColor.RED),
                player("Bob", 0, TotemColor.BLUE)
        ));
        TurnState turnState = new TurnState();
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(players.get(1), players.get(0)), 1, 4, RoundPhase.PLAYER_DRAWS);
        GameTestSupport.RecordingBoard board = new GameTestSupport.RecordingBoard(players);
        Game game = new Game(77, players, turnState, turnOrderTrack, board, Era.second, GameStatus.IN_PROGRESS);

        players.clear();
        game.setCurrentEra(Era.third);
        game.setStatus(GameStatus.FINISHED);

        assertEquals(77, game.getId());
        assertEquals(List.of("Alice", "Bob"), game.getPlayers().stream().map(Player::getNickname).toList());
        assertThrows(UnsupportedOperationException.class, () -> game.getPlayers().add(player("Carol", 0, TotemColor.BLACK)));
        assertSame(board, game.getBoard());
        assertSame(turnState, game.getTurnState());
        assertSame(turnOrderTrack, game.getTurnOrderTrack());
        assertEquals(Era.third, game.getCurrentEra());
        assertEquals(GameStatus.FINISHED, game.getStatus());
    }

    @Test
    void setupBuildsBoardAppliesInitialFoodAndStartsFirstPlacementPhase() {
        ArrayList<Player> players = players("Alice", "Bob", "Carol", "Dave");
        GameTestSupport.RecordingBoard board = new GameTestSupport.RecordingBoard(players);
        TurnState turnState = new TurnState(TurnPhase.WAITING_FOR_CARD_SELECTION, java.util.Map.of(players.get(0), 1), java.util.Map.of(players.get(0), 1));
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(players.get(1), players.get(0)), 1, 4, RoundPhase.RESOLVING_EFFECTS);
        Game game = new Game(1, players, turnState, turnOrderTrack, board);

        game.setup();

        assertTrue(board.buildOfferPathCalled);
        assertTrue(board.setupInitialRowsCalled);
        assertEquals(List.of(2, 3, 3, 4), players.stream().map(Player::getFood).toList());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertEquals(Era.first, game.getCurrentEra());
        assertEquals(5, turnOrderTrack.getRoundNumber());
        assertEquals(RoundPhase.PLACING_TOTEMS, turnOrderTrack.getCurrentRoundPhase());
        assertEquals(players, turnOrderTrack.getTurnOrder());
        assertEquals(TurnPhase.TURN_COMPLETED, turnState.getPhase());
        assertEquals(0, turnState.getRemainingUpperChoices(players.get(0)));
    }

    @Test
    void setupRejectsCallsOutsideSetupStatus() {
        ArrayList<Player> players = players("Alice");
        Game game = new Game(
                1,
                players,
                new TurnState(),
                new TurnOrderTrack(List.of(players.get(0)), 0, 1, RoundPhase.PLACING_TOTEMS),
                new GameTestSupport.RecordingBoard(players),
                Era.first,
                GameStatus.IN_PROGRESS
        );

        assertGameError(game, InvalidGameStatusException.class, game::setup);
    }
}
