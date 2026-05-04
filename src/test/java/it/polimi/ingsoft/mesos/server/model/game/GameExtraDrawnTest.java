package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Hunter;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static it.polimi.ingsoft.mesos.server.model.game.GameTestSupport.assertGameError;
import static it.polimi.ingsoft.mesos.server.model.game.GameTestSupport.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameExtraDrawnTest {

    @Test
    void extraDrawnPaysBuildingCostAddsBuildingAndPassivelyResetsRoundAfterLastBonusPlayer() {
        Player alice = player("Alice", 5, TotemColor.RED);
        alice.getTribe().addBuilding(extraDrawBuilding(90));
        BuildingCard chosenBuilding = new BuildingCard(10, Era.first, 3, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        GameTestSupport.RecordingBoard board = new GameTestSupport.RecordingBoard(List.of(alice));
        board.cardRow = CardRow.UPPER;
        board.upperCards = List.of(chosenBuilding);
        board.upperDraftableCards = List.of(chosenBuilding);
        board.nextRoundTurnOrder = List.of(alice);
        Game game = gameInExtraDraws(List.of(alice), board, List.of(alice), 1);

        game.extraDrawn(alice, chosenBuilding.getId());

        assertEquals(2, alice.getFood());
        assertTrue(alice.getTribe().getBuildings().contains(chosenBuilding));
        assertTrue(board.upperRemoved);
        assertTrue(board.resolveLowerEventsCalled);
        assertTrue(board.resetBoardAfterRoundCalled);
        assertEquals(RoundPhase.PLACING_TOTEMS, game.getTurnOrderTrack().getCurrentRoundPhase());
    }

    @Test
    void extraDrawnAdvancesToNextBonusPlayerWhenOrderContinues() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Player bob = player("Bob", 5, TotemColor.BLUE);
        alice.getTribe().addBuilding(extraDrawBuilding(90));
        bob.getTribe().addBuilding(extraDrawBuilding(91));
        Hunter hunter = hunter(20);
        GameTestSupport.RecordingBoard board = new GameTestSupport.RecordingBoard(List.of(alice, bob));
        board.cardRow = CardRow.LOWER;
        board.lowerCards = List.of(hunter);
        board.lowerDraftableCards = List.of(hunter);
        Game game = gameInExtraDraws(List.of(alice, bob), board, List.of(alice, bob), 1);

        game.extraDrawn(alice, hunter.getId());

        assertEquals(RoundPhase.EXTRA_DRAWS, game.getTurnOrderTrack().getCurrentRoundPhase());
        assertEquals(bob, game.getTurnOrderTrack().getCurrentPlayer());
        assertTrue(board.lowerRemoved);
        assertTrue(alice.getTribe().getCharacters().contains(hunter));
    }

    @Test
    void extraDrawnOnFinalRoundTriggersFinalScoringThroughPassivePhases() {
        Player alice = player("Alice", 5, TotemColor.RED);
        alice.getTribe().addBuilding(extraDrawBuilding(90));
        alice.getTribe().addBuilding(new BuildingCard(120, Era.third, 0, 4, BenefitType.FINAL_BONUS_BIG_PP_REWARD));
        Hunter hunter = hunter(21);
        GameTestSupport.RecordingBoard board = new GameTestSupport.RecordingBoard(List.of(alice));
        board.cardRow = CardRow.LOWER;
        board.lowerCards = List.of(hunter);
        board.lowerDraftableCards = List.of(hunter);
        board.nextRoundTurnOrder = List.of(alice);
        Game game = gameInExtraDraws(List.of(alice), board, List.of(alice), 10);

        game.extraDrawn(alice, hunter.getId());

        assertTrue(board.resolveAllEventsCalled);
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals(5, alice.getPrestigePoints());
    }

    @Test
    void extraDrawnRejectsWrongStatusPhasePlayerPermissionAndMissingCard() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Player bob = player("Bob", 5, TotemColor.BLUE);
        alice.getTribe().addBuilding(extraDrawBuilding(90));
        Hunter hunter = hunter(22);
        GameTestSupport.RecordingBoard board = new GameTestSupport.RecordingBoard(List.of(alice, bob));
        board.cardRow = CardRow.LOWER;
        board.lowerCards = List.of(hunter);
        board.lowerDraftableCards = List.of(hunter);

        Game wrongStatusGame = new Game(1, new ArrayList<>(List.of(alice)), new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.EXTRA_DRAWS), board, Era.first, GameStatus.SETUP);
        assertGameError(wrongStatusGame, InvalidGameStatusException.class, () -> wrongStatusGame.extraDrawn(alice, hunter.getId()));

        Game wrongPhaseGame = new Game(1, new ArrayList<>(List.of(alice)), new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS), board, Era.first, GameStatus.IN_PROGRESS);
        assertGameError(wrongPhaseGame, InvalidGamePhaseException.class, () -> wrongPhaseGame.extraDrawn(alice, hunter.getId()));

        Game wrongPlayerGame = gameInExtraDraws(List.of(alice, bob), board, List.of(alice, bob), 1);
        assertGameError(wrongPlayerGame, NotPlayersTurnException.class, () -> wrongPlayerGame.extraDrawn(bob, hunter.getId()));

        Player carol = player("Carol", 5, TotemColor.WHITE);
        Game noPermissionGame = gameInExtraDraws(List.of(carol), board, List.of(carol), 1);
        assertGameError(noPermissionGame, IllegalStateException.class, () -> noPermissionGame.extraDrawn(carol, hunter.getId()));

        GameTestSupport.RecordingBoard missingBoard = new GameTestSupport.RecordingBoard(List.of(alice));
        missingBoard.cardRow = CardRow.NOT_PRESENT;
        Game missingCardGame = gameInExtraDraws(List.of(alice), missingBoard, List.of(alice), 1);
        assertGameError(missingCardGame, CardNotPresentException.class, () -> missingCardGame.extraDrawn(alice, hunter.getId()));
    }

    private static Game gameInExtraDraws(List<Player> players,
                                         GameTestSupport.RecordingBoard board,
                                         List<Player> extraDrawOrder,
                                         int roundNumber) {
        return new Game(
                1,
                new ArrayList<>(players),
                new TurnState(),
                new TurnOrderTrack(extraDrawOrder, 0, roundNumber, RoundPhase.EXTRA_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );
    }

    private static BuildingCard extraDrawBuilding(int id) {
        return new BuildingCard(id, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD);
    }

    private static Hunter hunter(int id) {
        return new Hunter(id, Era.first, false, TribeCardType.HUNTER);
    }
}
