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

class GameChooseCardTest {

    @Test
    void chooseCardPaysBuildingCostAddsBuildingAndPassivelyResetsTheRound() {
        Player alice = player("Alice", 5, TotemColor.RED);
        BuildingCard building = new BuildingCard(10, Era.first, 3, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        GameTestSupport.RecordingBoard board = boardWithUpper(alice, building);
        board.nextRoundTurnOrder = List.of(alice);
        TurnState turnState = registeredTurnState(alice, 1, 0);
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS);
        Game game = inProgressGame(List.of(alice), turnState, turnOrderTrack, board);

        game.chooseCard(alice, building.getId());

        assertEquals(2, alice.getFood());
        assertTrue(alice.getTribe().getBuildings().contains(building));
        assertTrue(board.upperRemoved);
        assertTrue(board.returnedPlayers.contains(alice));
        assertTrue(board.resolveLowerEventsCalled);
        assertTrue(board.resetBoardAfterRoundCalled);
        assertEquals(RoundPhase.PLACING_TOTEMS, turnOrderTrack.getCurrentRoundPhase());
        assertEquals(2, turnOrderTrack.getRoundNumber());
    }

    @Test
    void chooseCardAddsCharacterAndAdvancesToNextDrawablePlayer() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Player bob = player("Bob", 5, TotemColor.BLUE);
        Hunter hunter = hunter(21);
        BuildingCard bobBuilding = new BuildingCard(31, Era.first, 1, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        GameTestSupport.RecordingBoard board = boardWithLower(alice, hunter);
        board.upperDraftableCardsByPlayer.put(bob, List.of(bobBuilding));
        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(alice, 0, 1);
        turnState.registerDrawableCards(bob, 1, 0);
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(alice, bob), 0, 1, RoundPhase.PLAYER_DRAWS);
        Game game = inProgressGame(List.of(alice, bob), turnState, turnOrderTrack, board);

        game.chooseCard(alice, hunter.getId());

        assertEquals(bob, turnOrderTrack.getCurrentPlayer());
        assertEquals(RoundPhase.PLAYER_DRAWS, turnOrderTrack.getCurrentRoundPhase());
        assertTrue(board.returnedPlayers.contains(alice));
        assertTrue(board.lowerRemoved);
        assertTrue(alice.getTribe().getCharacters().contains(hunter));
    }

    @Test
    void chooseCardKeepsTurnOpenWhenPlayerStillHasAnotherAvailableRowChoice() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Hunter lowerHunter = hunter(25);
        BuildingCard upperBuilding = new BuildingCard(34, Era.first, 1, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        GameTestSupport.RecordingBoard board = boardWithLower(alice, lowerHunter);
        board.upperDraftableCardsByPlayer.put(alice, List.of(upperBuilding));
        board.nextRoundTurnOrder = List.of(alice);
        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(alice, 1, 1);
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS);
        Game game = inProgressGame(List.of(alice), turnState, turnOrderTrack, board);

        game.chooseCard(alice, lowerHunter.getId());

        assertEquals(RoundPhase.PLAYER_DRAWS, turnOrderTrack.getCurrentRoundPhase());
        assertEquals(alice, turnOrderTrack.getCurrentPlayer());
        assertEquals(1, turnState.getRemainingUpperChoices(alice));
        assertEquals(0, turnState.getRemainingLowerChoices(alice));
        assertTrue(turnState.isWaitingForCardSelection());
        assertTrue(board.returnedPlayers.isEmpty());
    }

    @Test
    void chooseCardSkipsPlayersWithoutDraftableCards() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Player bob = player("Bob", 5, TotemColor.BLUE);
        Player carol = player("Carol", 5, TotemColor.WHITE);
        Hunter hunter = hunter(22);
        BuildingCard carolBuilding = new BuildingCard(32, Era.first, 1, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        GameTestSupport.RecordingBoard board = boardWithLower(alice, hunter);
        board.upperDraftableCardsByPlayer.put(carol, List.of(carolBuilding));
        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(alice, 0, 1);
        turnState.registerDrawableCards(bob, 1, 1);
        turnState.registerDrawableCards(carol, 1, 0);
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(alice, bob, carol), 0, 1, RoundPhase.PLAYER_DRAWS);
        Game game = inProgressGame(List.of(alice, bob, carol), turnState, turnOrderTrack, board);

        game.chooseCard(alice, hunter.getId());

        assertEquals(carol, turnOrderTrack.getCurrentPlayer());
        assertTrue(board.returnedPlayers.contains(alice));
        assertTrue(board.returnedPlayers.contains(bob));
        assertEquals(0, turnState.getRemainingUpperChoices(bob));
        assertEquals(0, turnState.getRemainingLowerChoices(bob));
    }

    @Test
    void chooseCardFiltersExtraDrawOrderToEligiblePlayersWithDraftableCards() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Player bob = player("Bob", 5, TotemColor.BLUE);
        alice.getTribe().addBuilding(new BuildingCard(99, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD));
        bob.getTribe().addBuilding(new BuildingCard(100, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD));
        Hunter hunter = hunter(23);
        BuildingCard bonusCard = new BuildingCard(33, Era.first, 1, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        GameTestSupport.RecordingBoard board = boardWithLower(alice, hunter);
        board.nextRoundTurnOrder = List.of(bob, alice);
        board.upperDraftableCardsByPlayer.put(alice, List.of(bonusCard));
        TurnState turnState = registeredTurnState(alice, 0, 1);
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS);
        Game game = inProgressGame(List.of(alice, bob), turnState, turnOrderTrack, board);

        game.chooseCard(alice, hunter.getId());

        assertEquals(RoundPhase.EXTRA_DRAWS, turnOrderTrack.getCurrentRoundPhase());
        assertEquals(List.of(alice), turnOrderTrack.getTurnOrder());
    }

    @Test
    void chooseCardRejectsInvalidStatusPhasePlayerAndCardSelection() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Player bob = player("Bob", 5, TotemColor.BLUE);
        Hunter hunter = hunter(24);
        GameTestSupport.RecordingBoard board = boardWithLower(alice, hunter);

        Game wrongStatusGame = new Game(1, new ArrayList<>(List.of(alice)), registeredTurnState(alice, 0, 1),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS), board, Era.first, GameStatus.SETUP);
        assertGameError(wrongStatusGame, InvalidGameStatusException.class, () -> wrongStatusGame.chooseCard(alice, hunter.getId()));

        Game wrongPhaseGame = inProgressGame(List.of(alice), registeredTurnState(alice, 0, 1),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLACING_TOTEMS), board);
        assertGameError(wrongPhaseGame, InvalidGamePhaseException.class, () -> wrongPhaseGame.chooseCard(alice, hunter.getId()));

        Game wrongPlayerGame = inProgressGame(List.of(alice, bob), registeredTurnState(alice, 0, 1),
                new TurnOrderTrack(List.of(alice, bob), 0, 1, RoundPhase.PLAYER_DRAWS), board);
        assertGameError(wrongPlayerGame, NotPlayersTurnException.class, () -> wrongPlayerGame.chooseCard(bob, hunter.getId()));

        GameTestSupport.RecordingBoard missingBoard = new GameTestSupport.RecordingBoard(List.of(alice));
        missingBoard.cardRow = CardRow.NOT_PRESENT;
        Game missingCardGame = inProgressGame(List.of(alice), registeredTurnState(alice, 0, 1),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS), missingBoard);
        assertGameError(missingCardGame, CardNotPresentException.class, () -> missingCardGame.chooseCard(alice, hunter.getId()));

        Game blockedRowGame = inProgressGame(List.of(alice), registeredTurnState(alice, 1, 0),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS), board);
        assertGameError(blockedRowGame, CardRowSelectionNotAllowedException.class, () -> blockedRowGame.chooseCard(alice, hunter.getId()));
    }

    private static Game inProgressGame(List<Player> players, TurnState turnState, TurnOrderTrack turnOrderTrack, GameTestSupport.RecordingBoard board) {
        return new Game(1, new ArrayList<>(players), turnState, turnOrderTrack, board, Era.first, GameStatus.IN_PROGRESS);
    }

    private static TurnState registeredTurnState(Player player, int upperChoices, int lowerChoices) {
        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(player, upperChoices, lowerChoices);
        return turnState;
    }

    private static GameTestSupport.RecordingBoard boardWithUpper(Player player, BuildingCard card) {
        GameTestSupport.RecordingBoard board = new GameTestSupport.RecordingBoard(List.of(player));
        board.cardRow = CardRow.UPPER;
        board.upperCards = List.of(card);
        board.upperDraftableCardsByPlayer.put(player, List.of(card));
        board.nextRoundTurnOrder = List.of(player);
        return board;
    }

    private static GameTestSupport.RecordingBoard boardWithLower(Player player, Hunter card) {
        GameTestSupport.RecordingBoard board = new GameTestSupport.RecordingBoard(List.of(player));
        board.cardRow = CardRow.LOWER;
        board.lowerCards = List.of(card);
        board.lowerDraftableCardsByPlayer.put(player, List.of(card));
        board.nextRoundTurnOrder = List.of(player);
        return board;
    }

    private static Hunter hunter(int id) {
        return new Hunter(id, Era.first, false, TribeCardType.HUNTER);
    }
}
