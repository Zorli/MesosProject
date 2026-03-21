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
import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLifecycleTest {

    @Test
    void constructorAndSettersExposeRestoredSnapshotState() {
        ArrayList<Player> players = new ArrayList<>(List.of(player("Alice", TotemColor.RED), player("Bob", TotemColor.BLUE)));
        RecordingBoard board = new RecordingBoard();
        TurnState turnState = new TurnState();
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(players.get(1), players.get(0)), 1, 4, RoundPhase.PLAYER_DRAWS);
        Game game = new Game(77, players, turnState, turnOrderTrack, board, Era.second, GameStatus.IN_PROGRESS);

        players.clear();
        game.setCurrentEra(Era.third);
        game.setStatus(GameStatus.FINISHED);

        assertEquals(77, game.getId());
        assertEquals(List.of("Alice", "Bob"), game.getPlayers().stream().map(Player::getNickname).toList());
        assertThrows(UnsupportedOperationException.class, () -> game.getPlayers().add(player("Carol", TotemColor.BLACK)));
        assertSame(board, game.getBoard());
        assertSame(turnState, game.getTurnState());
        assertSame(turnOrderTrack, game.getTurnOrderTrack());
        assertEquals(Era.third, game.getCurrentEra());
        assertEquals(GameStatus.FINISHED, game.getStatus());
    }

    @Test
    void setupBuildsBoardInitializesFirstRoundAndMarksGameInProgress() {
        ArrayList<Player> players = new ArrayList<>(List.of(player("Alice", TotemColor.RED), player("Bob", TotemColor.BLUE)));
        RecordingBoard board = new RecordingBoard();
        TurnState turnState = new TurnState(TurnPhase.WAITING_FOR_CARD_SELECTION, java.util.Map.of(players.get(0), 1), java.util.Map.of(players.get(0), 1));
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(List.of(players.get(1), players.get(0)), 1, 4, RoundPhase.RESOLVING_EFFECTS);
        Game game = new Game(1, players, turnState, turnOrderTrack, board);

        game.setup();

        assertTrue(board.buildOfferPathCalled);
        assertTrue(board.setupInitialRowsCalled);
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
        ArrayList<Player> players = new ArrayList<>(List.of(player("Alice", TotemColor.RED)));
        Game game = new Game(1, players, new TurnState(), new TurnOrderTrack(List.of(players.get(0)), 0, 1, RoundPhase.PLACING_TOTEMS), new RecordingBoard(), Era.first, GameStatus.IN_PROGRESS);

        assertThrows(InvalidGameStatusException.class, game::setup);
    }

    @Test
    void moveTotemRejectsWrongGameStatus() {
        ArrayList<Player> players = new ArrayList<>(List.of(player("Alice", TotemColor.RED)));
        Game game = new Game(1, players, new TurnState(), new TurnOrderTrack(List.of(players.get(0)), 0, 1, RoundPhase.PLACING_TOTEMS), new RecordingBoard(), Era.first, GameStatus.SETUP);

        assertThrows(InvalidGameStatusException.class, () -> game.moveTotem(players.get(0), "A"));
    }

    @Test
    void moveTotemRejectsNullTile() {
        ArrayList<Player> players = new ArrayList<>(List.of(player("Alice", TotemColor.RED), player("Bob", TotemColor.BLUE)));
        Game game = new Game(1, players, new TurnState(), new TurnOrderTrack(), new RecordingBoard());
        game.setup();

        assertThrows(TileNotAvailableException.class, () -> game.moveTotem(players.get(0), null));
    }

    @Test
    void chooseCardRejectsWrongStatusPhaseAndPlayer() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        DummyCharacterCard character = new DummyCharacterCard(21);
        RecordingBoard board = new RecordingBoard();
        board.cardRow = CardRow.LOWER;
        board.lowerDraftableCards = List.of(character);

        Game wrongStatusGame = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS),
                board,
                Era.first,
                GameStatus.SETUP
        );
        assertThrows(InvalidGameStatusException.class, () -> wrongStatusGame.chooseCard(alice, character));

        TurnState phaseTurnState = new TurnState();
        phaseTurnState.registerDrawableCards(alice, 0, 1);
        Game wrongPhaseGame = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                phaseTurnState,
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLACING_TOTEMS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );
        assertThrows(InvalidGamePhaseException.class, () -> wrongPhaseGame.chooseCard(alice, character));

        TurnState wrongPlayerTurnState = new TurnState();
        wrongPlayerTurnState.registerDrawableCards(alice, 0, 1);
        Game wrongPlayerGame = new Game(
                1,
                new ArrayList<>(List.of(alice, bob)),
                wrongPlayerTurnState,
                new TurnOrderTrack(List.of(alice, bob), 0, 1, RoundPhase.PLAYER_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );
        assertThrows(NotPlayersTurnException.class, () -> wrongPlayerGame.chooseCard(bob, character));
    }

    @Test
    void chooseCardRejectsUndrawableLowerCardsAndBrokenTurnState() {
        Player alice = player("Alice", TotemColor.RED);
        DummyCharacterCard character = new DummyCharacterCard(22);
        RecordingBoard board = new RecordingBoard();
        board.cardRow = CardRow.LOWER;

        TurnState undrawableTurnState = new TurnState();
        undrawableTurnState.registerDrawableCards(alice, 0, 1);
        Game undrawableGame = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                undrawableTurnState,
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        assertThrows(CardNotDrawableException.class, () -> undrawableGame.chooseCard(alice, character));

        FaultyTurnState faultyTurnState = new FaultyTurnState();
        faultyTurnState.registerDrawableCards(alice, 0, 1);
        Game brokenTurnStateGame = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                faultyTurnState,
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        assertThrows(InvalidTurnPhaseException.class, () -> brokenTurnStateGame.chooseCard(alice, character));
    }

    @Test
    void chooseCardRejectsUndrawableUpperCardsAndLowerRowSelectionsWithoutQuota() {
        Player alice = player("Alice", TotemColor.RED);
        DummyBuildingCard building = new DummyBuildingCard(30, 2);
        DummyCharacterCard character = new DummyCharacterCard(31);
        RecordingBoard board = new RecordingBoard();

        TurnState undrawableUpperTurnState = new TurnState();
        undrawableUpperTurnState.registerDrawableCards(alice, 1, 0);
        board.cardRow = CardRow.UPPER;
        Game undrawableUpperGame = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                undrawableUpperTurnState,
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );
        assertThrows(CardNotDrawableException.class, () -> undrawableUpperGame.chooseCard(alice, building));

        TurnState blockedLowerTurnState = new TurnState();
        blockedLowerTurnState.registerDrawableCards(alice, 1, 0);
        board.cardRow = CardRow.LOWER;
        board.lowerDraftableCards = List.of(character);
        Game blockedLowerGame = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                blockedLowerTurnState,
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );
        assertThrows(CardRowSelectionNotAllowedException.class, () -> blockedLowerGame.chooseCard(alice, character));
    }

    @Test
    void extraDrawnRejectsWrongStatusPhasePlayerAndMissingCard() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        alice.getTribe().addBuilding(new ExtraDrawPermissionBuildingCard(90));
        DummyCharacterCard character = new DummyCharacterCard(23);
        RecordingBoard board = new RecordingBoard();

        Game wrongStatusGame = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.EXTRA_DRAWS),
                board,
                Era.first,
                GameStatus.SETUP
        );
        assertThrows(InvalidGameStatusException.class, () -> wrongStatusGame.extraDrawn(alice, character));

        Game wrongPhaseGame = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.PLAYER_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );
        assertThrows(InvalidGamePhaseException.class, () -> wrongPhaseGame.extraDrawn(alice, character));

        Game wrongPlayerGame = new Game(
                1,
                new ArrayList<>(List.of(alice, bob)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice, bob), 0, 1, RoundPhase.EXTRA_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );
        assertThrows(NotPlayersTurnException.class, () -> wrongPlayerGame.extraDrawn(bob, character));

        board.cardRow = CardRow.NOT_PRESENT;
        Game missingCardGame = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.EXTRA_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );
        assertThrows(CardNotPresentException.class, () -> missingCardGame.extraDrawn(alice, character));
    }

    @Test
    void extraDrawnAdvancesToNextEligiblePlayerWhenRoundContinues() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        alice.getTribe().addBuilding(new ExtraDrawPermissionBuildingCard(90));
        bob.getTribe().addBuilding(new ExtraDrawPermissionBuildingCard(91));
        DummyCharacterCard character = new DummyCharacterCard(24);
        RecordingBoard board = new RecordingBoard();
        board.cardRow = CardRow.LOWER;
        board.lowerDraftableCards = List.of(character);

        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice, bob)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice, bob), 0, 1, RoundPhase.EXTRA_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        game.extraDrawn(alice, character);

        assertEquals(RoundPhase.EXTRA_DRAWS, game.getTurnOrderTrack().getCurrentRoundPhase());
        assertEquals(bob, game.getTurnOrderTrack().getCurrentPlayer());
        assertTrue(board.lowerRemoved);
    }

    @Test
    void extraDrawnRejectsUndrawableUpperAndLowerCards() {
        Player alice = player("Alice", TotemColor.RED);
        alice.getTribe().addBuilding(new ExtraDrawPermissionBuildingCard(92));
        DummyBuildingCard building = new DummyBuildingCard(32, 1);
        DummyCharacterCard character = new DummyCharacterCard(33);
        RecordingBoard board = new RecordingBoard();

        board.cardRow = CardRow.UPPER;
        Game undrawableUpperGame = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.EXTRA_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );
        assertThrows(CardNotDrawableException.class, () -> undrawableUpperGame.extraDrawn(alice, building));

        board.cardRow = CardRow.LOWER;
        Game undrawableLowerGame = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.EXTRA_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );
        assertThrows(CardNotDrawableException.class, () -> undrawableLowerGame.extraDrawn(alice, character));
    }

    @Test
    void resolveEffectsAndResetBoardRejectWrongGameStatus() {
        Player alice = player("Alice", TotemColor.RED);
        Game resolveEffectsGame = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.RESOLVING_EFFECTS),
                new RecordingBoard(),
                Era.first,
                GameStatus.SETUP
        );
        assertThrows(InvalidGameStatusException.class, resolveEffectsGame::resolveEffects);

        Game resetBoardGame = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.RESETTING_BOARD),
                new RecordingBoard(),
                Era.first,
                GameStatus.SETUP
        );
        assertThrows(InvalidGameStatusException.class, resetBoardGame::resetBoard);
    }

    @Test
    void moveTotemRegistersDrawableCardsReturnedByBoard() {
        ArrayList<Player> players = new ArrayList<>(List.of(player("Alice", TotemColor.RED), player("Bob", TotemColor.BLUE)));
        RecordingBoard board = new RecordingBoard();
        board.drawableCards = new int[]{2, 1};
        TurnState turnState = new TurnState();
        Game game = new Game(1, players, turnState, new TurnOrderTrack(), board);
        game.setup();

        game.moveTotem(players.get(0), "A");

        assertArrayEquals(new int[]{2, 1}, new int[]{turnState.getRemainingUpperChoices(players.get(0)), turnState.getRemainingLowerChoices(players.get(0))});
    }

    private static Player player(String nickname, TotemColor color) {
        Player player = new Player(nickname, 5, 0, color, null);
        player.initializeGameAttributes();
        return player;
    }

    private static final class RecordingBoard extends Board {
        private boolean buildOfferPathCalled;
        private boolean setupInitialRowsCalled;
        private CardRow cardRow = CardRow.NOT_PRESENT;
        private List<Card> upperDraftableCards = List.of();
        private List<Card> lowerDraftableCards = List.of();
        private boolean lowerRemoved;
        private int[] drawableCards = new int[]{1, 1};

        private RecordingBoard() {
            super(null, null, new ArrayList<>());
        }

        @Override
        public void buildOfferPath() {
            this.buildOfferPathCalled = true;
        }

        @Override
        public void setupInitialRows() {
            this.setupInitialRowsCalled = true;
        }

        @Override
        public boolean isSpaceAvailable(String targetCode) {
            return "A".equals(targetCode) || "B".equals(targetCode) || "C".equals(targetCode);
        }

        @Override
        public void placePlayerOnOfferSpace(Player player, String targetCode) {
        }

        @Override
        public int[] returnNumberDrawableCard(Player player) {
            return this.drawableCards;
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
        public void removeLowerCard(Card card) {
            this.lowerRemoved = true;
        }
    }

    private static final class DummyCharacterCard extends CharacterCard {
        private DummyCharacterCard(int id) {
            super(id, Era.first, TribeCardType.GATHERER);
        }
    }

    private static final class DummyBuildingCard extends BuildingCard {
        private DummyBuildingCard(int id, int foodCost) {
            super(id, Era.first, foodCost, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        }
    }

    private static final class ExtraDrawPermissionBuildingCard extends BuildingCard {
        private ExtraDrawPermissionBuildingCard(int id) {
            super(id, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD);
        }
    }

    private static final class FaultyTurnState extends TurnState {
        @Override
        public void startCardSelection(Player player) {
        }
    }
}
