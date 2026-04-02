package it.polimi.ingsoft.mesos.server.model.game;

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

/**
 * Focused tests for the bonus-draw branch of the {@link Game} turn flow.
 */
class GameExtraDrawnTest {

    @Test
    void extraDrawnPaysBuildingCostAddsBuildingAndMovesToResolvingEffectsAfterLastPlayer() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Player bob = player("Bob", 5, TotemColor.BLUE);
        alice.getTribe().addBuilding(new ExtraDrawPermissionBuildingCard(90, 0));
        DummyBuildingCard chosenBuilding = new DummyBuildingCard(10, 3);
        RecordingBoard board = new RecordingBoard();
        board.cardRow = CardRow.UPPER;
        board.upperDraftableCards = List.of(chosenBuilding);
        board.nextRoundTurnOrder = List.of(bob, alice);

        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice, bob)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.EXTRA_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        game.extraDrawn(alice, chosenBuilding);

        assertEquals(2, alice.getFood());
        assertTrue(alice.getTribe().getBuildings().contains(chosenBuilding));
        assertTrue(board.upperRemoved);
        assertEquals(RoundPhase.RESOLVING_EFFECTS, game.getTurnOrderTrack().getCurrentRoundPhase());
        assertEquals(List.of(bob, alice), game.getTurnOrderTrack().getTurnOrder());
    }

    @Test
    void extraDrawnRejectsPlayerWithoutExtraDrawBuilding() {
        Player alice = player("Alice", 5, TotemColor.RED);
        DummyCharacterCard character = new DummyCharacterCard(20);
        RecordingBoard board = new RecordingBoard();
        board.cardRow = CardRow.LOWER;
        board.lowerDraftableCards = List.of(character);
        board.nextRoundTurnOrder = List.of(alice);

        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 1, RoundPhase.EXTRA_DRAWS),
                board,
                Era.first,
                GameStatus.IN_PROGRESS
        );

        assertThrows(IllegalStateException.class, () -> game.extraDrawn(alice, character));
    }

    private static Player player(String nickname, int food, TotemColor color) {
        Player player = new Player(nickname, food, 0, color);
        return player;
    }

    /**
     * Minimal board double exposing only the hooks exercised by extra-draw scenarios.
     */
    private static final class RecordingBoard extends Board {
        private CardRow cardRow = CardRow.NOT_PRESENT;
        private List<Card> upperDraftableCards = List.of();
        private List<Card> lowerDraftableCards = List.of();
        private List<Player> nextRoundTurnOrder = List.of();
        private boolean upperRemoved;

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
        }

        @Override
        public List<Player> getNextRoundTurnOrder() {
            return this.nextRoundTurnOrder;
        }
    }

    /**
     * Lightweight building card used to verify extra-draw acquisition and payment.
     */
    private static final class DummyBuildingCard extends BuildingCard {
        private DummyBuildingCard(int id, int foodCost) {
            super(id, Era.first, foodCost, 1, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        }
    }

    /**
     * Building stub granting permission to perform one extra draw.
     */
    private static final class ExtraDrawPermissionBuildingCard extends BuildingCard {
        private ExtraDrawPermissionBuildingCard(int id, int foodCost) {
            super(id, Era.first, foodCost, 1, BenefitType.END_ROUND_EXTRA_CARD);
        }
    }

    /**
     * Lightweight character card used in negative extra-draw scenarios.
     */
    private static final class DummyCharacterCard extends CharacterCard {
        private DummyCharacterCard(int id) {
            super(id, Era.first, TribeCardType.GATHERER);
        }
    }
}
