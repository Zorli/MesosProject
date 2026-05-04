package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Hunter;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static it.polimi.ingsoft.mesos.server.model.game.GameTestSupport.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameResolveEffectsTest {

    @Test
    void passiveResolveEffectsRunsLowerEventsBeforeBoardResetInNormalRounds() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Hunter hunter = hunter(30);
        GameTestSupport.RecordingBoard board = boardWithOnlyDraw(alice, hunter);
        Game game = singleDrawGame(alice, board, 1);

        game.chooseCard(alice, hunter.getId());

        assertTrue(board.resolveLowerEventsCalled);
        assertTrue(board.resetBoardAfterRoundCalled);
        assertEquals(RoundPhase.PLACING_TOTEMS, game.getTurnOrderTrack().getCurrentRoundPhase());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
    }

    @Test
    void passiveResolveEffectsRunsAllEventsAndFinalScoringOnRoundTen() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Hunter hunter = hunter(31);
        GameTestSupport.RecordingBoard board = boardWithOnlyDraw(alice, hunter);
        Game game = singleDrawGame(alice, board, 10);

        game.chooseCard(alice, hunter.getId());

        assertTrue(board.resolveAllEventsCalled);
        assertEquals(RoundPhase.FINAL_SCORING, game.getTurnOrderTrack().getCurrentRoundPhase());
        assertEquals(GameStatus.FINISHED, game.getStatus());
    }

    private static Game singleDrawGame(Player player, GameTestSupport.RecordingBoard board, int roundNumber) {
        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(player, 0, 1);
        return new Game(
                1,
                new ArrayList<>(List.of(player)),
                turnState,
                new TurnOrderTrack(List.of(player), 0, roundNumber, RoundPhase.PLAYER_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );
    }

    private static GameTestSupport.RecordingBoard boardWithOnlyDraw(Player player, Hunter hunter) {
        GameTestSupport.RecordingBoard board = new GameTestSupport.RecordingBoard(List.of(player));
        board.cardRow = CardRow.LOWER;
        board.lowerCards = List.of(hunter);
        board.lowerDraftableCards = List.of(hunter);
        board.nextRoundTurnOrder = List.of(player);
        return board;
    }

    private static Hunter hunter(int id) {
        return new Hunter(id, Era.first, false, TribeCardType.HUNTER);
    }
}
