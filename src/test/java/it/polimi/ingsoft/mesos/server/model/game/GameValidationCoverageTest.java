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

class GameValidationCoverageTest {

    @Test
    void moveTotemRejectsWrongGameStatus() {
        Player alice = player("Alice", TotemColor.RED);
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLACING_TOTEMS),
                new GameTestSupport.RecordingBoard(List.of(alice)),
                Era.first,
                GameStatus.SETUP
        );

        assertGameError(game, InvalidGameStatusException.class, () -> game.moveTotem(alice, "A"));
    }

    @Test
    void chooseCardRejectsBrokenTurnStateThatDoesNotEnterSelectionPhase() {
        Player alice = player("Alice", TotemColor.RED);
        Hunter hunter = hunter(10);
        GameTestSupport.RecordingBoard board = lowerBoard(alice, hunter);
        FaultyTurnState turnState = new FaultyTurnState();
        turnState.registerDrawableCards(alice, 0, 1);
        Game game = inPlayerDraws(List.of(alice), turnState, board);

        assertGameError(game, InvalidTurnPhaseException.class, () -> game.chooseCard(alice, hunter.getId()));
    }

    @Test
    void chooseCardRejectsUndrawableCardsBeforeCheckingRowQuota() {
        Player alice = player("Alice", TotemColor.RED);
        BuildingCard upperBuilding = new BuildingCard(11, Era.first, 2, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        GameTestSupport.RecordingBoard upperBoard = upperBoard(alice, upperBuilding);
        upperBoard.upperDraftableCardsByPlayer.put(alice, List.of());
        Game upperGame = inPlayerDraws(List.of(alice), registeredTurnState(alice, 1, 0), upperBoard);
        assertGameError(upperGame, CardNotDrawableException.class, () -> upperGame.chooseCard(alice, upperBuilding.getId()));

        Hunter lowerHunter = hunter(12);
        GameTestSupport.RecordingBoard lowerBoard = lowerBoard(alice, lowerHunter);
        lowerBoard.lowerDraftableCardsByPlayer.put(alice, List.of());
        Game lowerGame = inPlayerDraws(List.of(alice), registeredTurnState(alice, 0, 1), lowerBoard);
        assertGameError(lowerGame, CardNotDrawableException.class, () -> lowerGame.chooseCard(alice, lowerHunter.getId()));
    }

    @Test
    void chooseCardRejectsUpperRowWhenPlayerHasOnlyLowerQuota() {
        Player alice = player("Alice", TotemColor.RED);
        BuildingCard upperBuilding = new BuildingCard(13, Era.first, 0, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        GameTestSupport.RecordingBoard board = upperBoard(alice, upperBuilding);
        Game game = inPlayerDraws(List.of(alice), registeredTurnState(alice, 0, 1), board);

        assertGameError(game, CardRowSelectionNotAllowedException.class, () -> game.chooseCard(alice, upperBuilding.getId()));
    }

    @Test
    void extraDrawnRejectsUndrawableUpperAndLowerCards() {
        Player alice = player("Alice", TotemColor.RED);
        alice.getTribe().addBuilding(new BuildingCard(90, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD));

        BuildingCard upperBuilding = new BuildingCard(14, Era.first, 2, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        GameTestSupport.RecordingBoard upperBoard = upperBoard(alice, upperBuilding);
        upperBoard.upperDraftableCardsByPlayer.put(alice, List.of());
        Game upperGame = inExtraDraws(List.of(alice), upperBoard);
        assertGameError(upperGame, CardNotDrawableException.class, () -> upperGame.extraDrawn(alice, upperBuilding.getId()));

        Hunter lowerHunter = hunter(15);
        GameTestSupport.RecordingBoard lowerBoard = lowerBoard(alice, lowerHunter);
        lowerBoard.lowerDraftableCardsByPlayer.put(alice, List.of());
        Game lowerGame = inExtraDraws(List.of(alice), lowerBoard);
        assertGameError(lowerGame, CardNotDrawableException.class, () -> lowerGame.extraDrawn(alice, lowerHunter.getId()));
    }

    private static Game inPlayerDraws(List<Player> players, TurnState turnState, GameTestSupport.RecordingBoard board) {
        return new Game(
                1,
                new ArrayList<>(players),
                turnState,
                new TurnOrderTrack(players, 0, 1, RoundPhase.PLAYER_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );
    }

    private static Game inExtraDraws(List<Player> players, GameTestSupport.RecordingBoard board) {
        return new Game(
                1,
                new ArrayList<>(players),
                new TurnState(),
                new TurnOrderTrack(players, 0, 1, RoundPhase.EXTRA_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );
    }

    private static TurnState registeredTurnState(Player player, int upperChoices, int lowerChoices) {
        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(player, upperChoices, lowerChoices);
        return turnState;
    }

    private static GameTestSupport.RecordingBoard upperBoard(Player player, BuildingCard card) {
        GameTestSupport.RecordingBoard board = new GameTestSupport.RecordingBoard(List.of(player));
        board.cardRow = CardRow.UPPER;
        board.upperCards = List.of(card);
        board.upperDraftableCardsByPlayer.put(player, List.of(card));
        board.nextRoundTurnOrder = List.of(player);
        return board;
    }

    private static GameTestSupport.RecordingBoard lowerBoard(Player player, Hunter card) {
        GameTestSupport.RecordingBoard board = new GameTestSupport.RecordingBoard(List.of(player));
        board.cardRow = CardRow.LOWER;
        board.lowerCards = List.of(card);
        board.lowerDraftableCardsByPlayer.put(player, List.of(card));
        board.nextRoundTurnOrder = List.of(player);
        return board;
    }

    private static Player player(String nickname, TotemColor color) {
        return new Player(nickname, 5, 0, color);
    }

    private static Hunter hunter(int id) {
        return new Hunter(id, Era.first, false, TribeCardType.HUNTER);
    }

    private static final class FaultyTurnState extends TurnState {
        @Override
        public void startCardSelection(Player player) {
        }
    }
}
