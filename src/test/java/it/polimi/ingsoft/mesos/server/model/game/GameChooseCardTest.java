package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Hunter;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the card-selection branch of the {@link Game} turn flow.
 */
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

        game.chooseCard(alice, building.getId());

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

        assertGameError(game, CardNotPresentException.class, () -> game.chooseCard(alice, character.getId()));
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

        assertGameError(game, CardRowSelectionNotAllowedException.class, () -> game.chooseCard(alice, building.getId()));
    }

    @Test
    void chooseCardAdvancesToNextPlayerWhenTheNextPlayerStillHasAvailableDraws() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Player bob = player("Bob", 5, TotemColor.BLUE);
        Hunter character = hunter(21);
        DummyBuildingCard bobBuilding = new DummyBuildingCard(31, 1);
        RecordingBoard board = new RecordingBoard();
        board.cardRow = CardRow.LOWER;
        board.lowerDraftableCardsByPlayer.put(alice, List.of(character));
        board.upperDraftableCardsByPlayer.put(bob, List.of(bobBuilding));
        board.nextRoundTurnOrder = List.of(alice, bob);

        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(alice, 0, 1);
        turnState.registerDrawableCards(bob, 1, 0);
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

        game.chooseCard(alice, character.getId());

        assertEquals(bob, turnOrderTrack.getCurrentPlayer());
        assertEquals(RoundPhase.PLAYER_DRAWS, turnOrderTrack.getCurrentRoundPhase());
        assertTrue(board.returnedPlayers.contains(alice));
        assertTrue(board.lowerRemoved);
        assertTrue(alice.getTribe().getCharacters().contains(character));
        assertEquals(0, turnState.getRemainingLowerChoices(alice));
    }

    @Test
    void chooseCardSkipsPlayersWhoseRegisteredChoicesAreNoLongerDraftableOnBoard() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Player bob = player("Bob", 5, TotemColor.BLUE);
        Player carol = player("Carol", 5, TotemColor.WHITE);
        Hunter character = hunter(23);
        DummyBuildingCard carolBuilding = new DummyBuildingCard(32, 1);
        RecordingBoard board = new RecordingBoard();
        board.cardRow = CardRow.LOWER;
        board.lowerDraftableCardsByPlayer.put(alice, List.of(character));
        board.upperDraftableCardsByPlayer.put(carol, List.of(carolBuilding));
        board.nextRoundTurnOrder = List.of(alice, bob, carol);

        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(alice, 0, 1);
        turnState.registerDrawableCards(bob, 1, 1);
        turnState.registerDrawableCards(carol, 1, 0);
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(alice, bob, carol), 0, 1, RoundPhase.PLAYER_DRAWS);
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice, bob, carol)),
                turnState,
                turnOrderTrack,
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        game.chooseCard(alice, character.getId());

        assertEquals(carol, turnOrderTrack.getCurrentPlayer());
        assertEquals(RoundPhase.PLAYER_DRAWS, turnOrderTrack.getCurrentRoundPhase());
        assertTrue(board.returnedPlayers.contains(alice));
        assertTrue(board.returnedPlayers.contains(bob));
        assertEquals(0, turnState.getRemainingUpperChoices(bob));
        assertEquals(0, turnState.getRemainingLowerChoices(bob));
    }

    @Test
    void chooseCardMovesToExtraDrawsWithPlayersEligibleForBonusDraw() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Player bob = player("Bob", 5, TotemColor.BLUE);
        alice.getTribe().addBuilding(new ExtraDrawBuildingCard(99, 0));
        Hunter character = hunter(22);
        DummyBuildingCard bonusDrawCard = new DummyBuildingCard(33, 1);
        RecordingBoard board = new RecordingBoard();
        board.cardRow = CardRow.LOWER;
        board.lowerDraftableCardsByPlayer.put(alice, List.of(character));
        board.upperDraftableCardsByPlayer.put(alice, List.of(bonusDrawCard));
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

        game.chooseCard(alice, character.getId());

        assertEquals(RoundPhase.EXTRA_DRAWS, turnOrderTrack.getCurrentRoundPhase());
        assertEquals(List.of(alice), turnOrderTrack.getTurnOrder());
        assertEquals(alice, turnOrderTrack.getCurrentPlayer());
    }

    @Test
    void chooseCardSkipsExtraDrawPhaseWhenEligiblePlayersCannotDraftAnyCard() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Player bob = player("Bob", 5, TotemColor.BLUE);
        alice.getTribe().addBuilding(new ExtraDrawBuildingCard(100, 0));
        Hunter character = hunter(24);
        RecordingBoard board = new RecordingBoard();
        board.cardRow = CardRow.LOWER;
        board.lowerDraftableCardsByPlayer.put(alice, List.of(character));
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

        game.chooseCard(alice, character.getId());

        assertEquals(RoundPhase.RESOLVING_EFFECTS, turnOrderTrack.getCurrentRoundPhase());
        assertEquals(List.of(bob, alice), turnOrderTrack.getTurnOrder());
    }

    private static Player player(String nickname, int food, TotemColor color) {
        Player player = new Player(nickname, food, 0, color);
        return player;
    }

    private static void assertGameError(Game game, Class<? extends RuntimeException> expectedError, Runnable action) {
        RecordingListener listener = new RecordingListener();
        long previousVersion = game.getStateVersion();
        game.addListener(listener);

        action.run();

        assertEquals(previousVersion, game.getStateVersion());
        ErrorPayload errorPayload = assertInstanceOf(ErrorPayload.class, listener.payload);
        assertEquals(expectedError.getSimpleName(), errorPayload.code);
    }

    private static final class RecordingListener implements Listener {
        private Object payload;

        @Override
        public void notifyUpdate(Object payload) {
            this.payload = payload;
        }
    }

    private static Hunter hunter(int id) {
        return new Hunter(id, Era.first, false, TribeCardType.HUNTER);
    }

    /**
     * Minimal board double exposing only the hooks exercised by choose-card scenarios.
     */
    private static final class RecordingBoard extends Board {
        private CardRow cardRow = CardRow.NOT_PRESENT;
        private List<Card> upperDraftableCards = List.of();
        private List<Card> lowerDraftableCards = List.of();
        private final java.util.Map<Player, List<Card>> upperDraftableCardsByPlayer = new java.util.HashMap<>();
        private final java.util.Map<Player, List<Card>> lowerDraftableCardsByPlayer = new java.util.HashMap<>();
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
            return this.upperDraftableCardsByPlayer.getOrDefault(player, this.upperDraftableCards);
        }

        @Override
        public List<Card> getDraftableLowerCards(Player player) {
            return this.lowerDraftableCardsByPlayer.getOrDefault(player, this.lowerDraftableCards);
        }

        @Override
        public void removeUpperCard(Card card) {
            this.upperRemoved = true;
            this.upperDraftableCards = this.upperDraftableCards.stream()
                    .filter(candidate -> !candidate.equals(card))
                    .toList();
            this.upperDraftableCardsByPlayer.replaceAll((player, cards) -> cards.stream()
                    .filter(candidate -> !candidate.equals(card))
                    .toList());
        }

        @Override
        public void removeLowerCard(Card card) {
            this.lowerRemoved = true;
            this.lowerDraftableCards = this.lowerDraftableCards.stream()
                    .filter(candidate -> !candidate.equals(card))
                    .toList();
            this.lowerDraftableCardsByPlayer.replaceAll((player, cards) -> cards.stream()
                    .filter(candidate -> !candidate.equals(card))
                    .toList());
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

    /**
     * Lightweight building card used to test payment and acquisition logic.
     */
    private static final class DummyBuildingCard extends BuildingCard {
        private DummyBuildingCard(int id, int foodCost) {
            super(id, Era.first, foodCost, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        }
    }

    /**
     * Lightweight character card used to test lower-row draft scenarios.
     */
    private static final class DummyCharacterCard extends CharacterCard {
        private DummyCharacterCard(int id) {
            super(id, Era.first, TribeCardType.GATHERER);
        }
    }

    /**
     * Building stub exposing the end-of-round extra-draw benefit.
     */
    private static final class ExtraDrawBuildingCard extends BuildingCard {
        private ExtraDrawBuildingCard(int id, int foodCost) {
            super(id, Era.first, foodCost, 1, BenefitType.END_ROUND_EXTRA_CARD);
        }
    }
}
