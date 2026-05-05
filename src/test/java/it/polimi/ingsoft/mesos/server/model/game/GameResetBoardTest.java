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

class GameResetBoardTest {

    @Test
    void passiveResetBoardAdvancesRoundClearsTurnStateAndKeepsNextRoundOrder() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Player bob = player("Bob", 5, TotemColor.BLUE);
        Hunter hunter = hunter(40);
        GameTestSupport.RecordingBoard board = boardWithLower(alice, hunter);
        board.nextRoundTurnOrder = List.of(bob, alice);
        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(alice, 0, 1);
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice, bob)),
                turnState,
                new TurnOrderTrack(List.of(alice), 0, 3, RoundPhase.PLAYER_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        game.chooseCard(alice, hunter.getId());

        assertTrue(board.resetBoardAfterRoundCalled);
        assertEquals(Era.first, game.getCurrentEra());
        assertEquals(4, game.getTurnOrderTrack().getRoundNumber());
        assertEquals(RoundPhase.PLACING_TOTEMS, game.getTurnOrderTrack().getCurrentRoundPhase());
        assertEquals(List.of(bob, alice), game.getTurnOrderTrack().getTurnOrder());
        assertEquals(0, game.getTurnState().getRemainingLowerChoices(alice));
    }

    @Test
    void passiveResetBoardAdvancesEraWhenBoardReportsEraChange() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Hunter hunter = hunter(41);
        GameTestSupport.RecordingBoard board = boardWithLower(alice, hunter);
        board.eraChanged = true;
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                registeredTurnState(alice),
                new TurnOrderTrack(List.of(alice), 0, 4, RoundPhase.PLAYER_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        game.chooseCard(alice, hunter.getId());

        assertEquals(Era.second, game.getCurrentEra());
        assertEquals(5, game.getTurnOrderTrack().getRoundNumber());
        assertEquals(RoundPhase.PLACING_TOTEMS, game.getTurnOrderTrack().getCurrentRoundPhase());
    }

    private static TurnState registeredTurnState(Player player) {
        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(player, 0, 1);
        return turnState;
    }

    private static GameTestSupport.RecordingBoard boardWithLower(Player player, Hunter hunter) {
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
