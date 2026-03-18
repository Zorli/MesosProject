package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameChooseCardTest {

    @Test
    void chooseCardPaysBuildingCostAddsBuildingAndMovesToResolvingEffectsWhenNoExtraDrawPlayersExist() {
        Player alice = player("Alice", 5, TotemColor.RED);
        DummyBuildingCard building = new DummyBuildingCard(10, 3);
        RecordingBoard board = new RecordingBoard();
        board.cardRow = CardRow.UPPER;
        board.upperDraftableCards = List.of(building);
        board.nextRoundTurnOrder = List.of(alice);

        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(alice, 1, 0);
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS);
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                turnState,
                turnOrderTrack,
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        game.chooseCard(alice, building);

        assertEquals(2, alice.getFood());
        assertTrue(alice.getTribe().getBuildings().contains(building));
        assertTrue(board.upperRemoved);
        assertTrue(board.returnedPlayers.contains(alice));
        assertEquals(RoundPhase.RESOLVING_EFFECTS, turnOrderTrack.getCurrentRoundPhase());
        assertEquals(List.of(alice), turnOrderTrack.getTurnOrder());
        assertEquals(0, turnState.getRemainingUpperChoices(alice));
    }

    @Test
    void chooseCardRejectsCardThatIsNotPresentOnBoard() {
        Player alice = player("Alice", 5, TotemColor.RED);
        DummyCharacterCard character = new DummyCharacterCard(20);
        RecordingBoard board = new RecordingBoard();
        board.cardRow = CardRow.NOT_PRESENT;
        board.nextRoundTurnOrder = List.of(alice);

        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(alice, 1, 0);
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS);
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                turnState,
                turnOrderTrack,
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        assertThrows(CardNotPresentException.class, () -> game.chooseCard(alice, character));
    }

    @Test
    void chooseCardRejectsUpperRowSelectionWhenPlayerHasNoUpperChoicesRemaining() {
        Player alice = player("Alice", 5, TotemColor.RED);
        DummyBuildingCard building = new DummyBuildingCard(11, 1);
        RecordingBoard board = new RecordingBoard();
        board.cardRow = CardRow.UPPER;
        board.upperDraftableCards = List.of(building);
        board.nextRoundTurnOrder = List.of(alice);

        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(alice, 0, 1);
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS);
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                turnState,
                turnOrderTrack,
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        assertThrows(CardRowSelectionNotAllowedException.class, () -> game.chooseCard(alice, building));
    }

    @Test
    void chooseCardAdvancesToNextPlayerWithoutSkippingDuringDrawPhase() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Player bob = player("Bob", 5, TotemColor.BLUE);
        DummyCharacterCard character = new DummyCharacterCard(21);
        RecordingBoard board = new RecordingBoard();
        board.cardRow = CardRow.LOWER;
        board.lowerDraftableCards = List.of(character);
        board.nextRoundTurnOrder = List.of(alice, bob);

        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(alice, 0, 1);
        turnState.registerDrawableCards(bob, 0, 0);
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(alice, bob), 0, 1, RoundPhase.PLAYER_DRAWS);
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice, bob)),
                turnState,
                turnOrderTrack,
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        game.chooseCard(alice, character);

        assertEquals(bob, turnOrderTrack.getCurrentPlayer());
        assertEquals(RoundPhase.PLAYER_DRAWS, turnOrderTrack.getCurrentRoundPhase());
        assertTrue(board.returnedPlayers.contains(alice));
    }

    @Test
    void chooseCardMovesToExtraDrawsWithPlayersEligibleForBonusDraw() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Player bob = player("Bob", 5, TotemColor.BLUE);
        alice.getTribe().addBuilding(new ExtraDrawBuildingCard(99, 0));
        DummyCharacterCard character = new DummyCharacterCard(22);
        RecordingBoard board = new RecordingBoard();
        board.cardRow = CardRow.LOWER;
        board.lowerDraftableCards = List.of(character);
        board.nextRoundTurnOrder = List.of(bob, alice);

        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(alice, 0, 1);
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS);
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice, bob)),
                turnState,
                turnOrderTrack,
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        game.chooseCard(alice, character);

        assertEquals(RoundPhase.EXTRA_DRAWS, turnOrderTrack.getCurrentRoundPhase());
        assertEquals(List.of(alice), turnOrderTrack.getTurnOrder());
        assertEquals(alice, turnOrderTrack.getCurrentPlayer());
    }

    private static Player player(String nickname, int food, TotemColor color) {
        Player player = new Player(nickname, food, 0, color, null);
        player.initializeGameAttributes();
        return player;
    }

    private static final class RecordingBoard extends Board {
        private CardRow cardRow = CardRow.NOT_PRESENT;
        private List<Card> upperDraftableCards = List.of();
        private List<Card> lowerDraftableCards = List.of();
        private List<Player> nextRoundTurnOrder = List.of();
        private boolean upperRemoved;
        private boolean lowerRemoved;
        private final List<Player> returnedPlayers = new ArrayList<>();

        private RecordingBoard() {
            super(null, null, new ArrayList<>());
        }

        @Override
        public CardRow getCardRow(Card card) {
            return this.cardRow;
        }

        @Override
        public List<Card> getDraftableUpperCards(Player player) {
            return this.upperDraftableCards;
        }

        @Override
        public List<Card> getDraftableLowerCards(Player player) {
            return this.lowerDraftableCards;
        }

        @Override
        public void removeUpperCard(Card card) {
            this.upperRemoved = true;
        }

        @Override
        public void removeLowerCard(Card card) {
            this.lowerRemoved = true;
        }

        @Override
        public void returnPlayerToStartingTile(Player player) {
            this.returnedPlayers.add(player);
        }

        @Override
        public List<Player> getNextRoundTurnOrder() {
            return this.nextRoundTurnOrder;
        }
    }

    private static final class DummyBuildingCard extends BuildingCard {
        private DummyBuildingCard(int id, int foodCost) {
            super(id, Era.first, foodCost, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        }
    }

    private static final class DummyCharacterCard extends CharacterCard {
        private DummyCharacterCard(int id) {
            super(id, Era.first, TribeCardType.HUNTER);
        }
    }

    private static final class ExtraDrawBuildingCard extends BuildingCard {
        private ExtraDrawBuildingCard(int id, int foodCost) {
            super(id, Era.first, foodCost, 1, BenefitType.END_ROUND_EXTRA_CARD);
        }
    }
}
