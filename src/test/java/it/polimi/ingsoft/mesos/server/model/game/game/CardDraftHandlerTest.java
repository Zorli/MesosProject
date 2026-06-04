package it.polimi.ingsoft.mesos.server.model.game.game;

import it.polimi.ingsoft.mesos.server.model.game.board.Board;
import it.polimi.ingsoft.mesos.server.model.game.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.Card;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Hunter;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.game.game.enums.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.CardNotPresentException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.CardNotDrawableException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.CardRowSelectionNotAllowedException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidGamePhaseException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidGameStatusException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.NotPlayersTurnException;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.server.model.game.player.Tribe;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CardDraftHandler}.
 *
 * <p>The handler is exercised with a recording board. The tests focus on the
 * card-draft contract: validate status/phase/turn, remove valid cards, apply
 * them to the player, consume quotas, and move to the next phase.
 */
class CardDraftHandlerTest {

    /**
     * A valid standard upper-row draw removes the card, adds it to the player's
     * tribe, clears the consumed quota, returns the player to the starting tile,
     * and ends standard drafting when no other useful draw exists.
     */
    @Test
    void chooseCardDrawsValidUpperCardAndCompletesTurn() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        Hunter hunter = hunter(10);
        context.board().upperCards.add(hunter);
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);
        context.draftState().registerDrawQuota(alice, 1, 0);

        context.handler().chooseCard(alice, hunter.getId());

        assertTrue(context.board().upperCards.isEmpty());
        assertEquals(List.of(alice), context.board().returnedToStartingTile);
        assertEquals(1, alice.getTribe().getCharactersSize());
        assertEquals(0, context.draftState().getRemainingUpperChoices(alice));
        assertEquals(RoundPhase.RESOLVING_EFFECTS, context.roundFlow().getCurrentRoundPhase());
    }

    /**
     * A valid lower-row draw consumes the lower quota and removes the card from
     * the lower row.
     */
    @Test
    void chooseCardDrawsValidLowerCard() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        Hunter hunter = hunter(13);
        context.board().lowerCards.add(hunter);
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);
        context.draftState().registerDrawQuota(alice, 0, 1);

        context.handler().chooseCard(alice, hunter.getId());

        assertTrue(context.board().lowerCards.isEmpty());
        assertEquals(1, alice.getTribe().getCharactersSize());
        assertEquals(0, context.draftState().getRemainingLowerChoices(alice));
    }

    /**
     * Building cards are paid and then added to the player's buildings.
     */
    @Test
    void chooseCardAppliesBuildingCardByPayingCostAndAddingItToTribe() {
        Player alice = new Player("Alice", 3, 0, TotemColor.RED);
        TestContext context = context(List.of(alice));
        BuildingCard building = new BuildingCard(20, Era.first, 2, 0, BenefitType.END_ROUND_TOTEM_POSITION);
        context.board().upperCards.add(building);
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);
        context.draftState().registerDrawQuota(alice, 1, 0);

        context.handler().chooseCard(alice, building.getId());

        assertEquals(1, alice.getFood());
        assertEquals(List.of(building), alice.getTribe().getBuildings());
    }

    /**
     * The selected row must match a positive remaining quota. A lower-row card
     * cannot be chosen when the player has only upper-row choices.
     */
    @Test
    void chooseCardRejectsRowWithoutRemainingQuota() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        Hunter hunter = hunter(11);
        context.board().lowerCards.add(hunter);
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);
        context.draftState().registerDrawQuota(alice, 1, 0);

        assertThrows(CardRowSelectionNotAllowedException.class, () -> context.handler().chooseCard(alice, hunter.getId()));
    }

    /**
     * Missing card identifiers are rejected as "not present on the board".
     */
    @Test
    void chooseCardRejectsMissingCard() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);
        context.draftState().registerDrawQuota(alice, 1, 0);

        assertThrows(CardNotPresentException.class, () -> context.handler().chooseCard(alice, 999));
    }

    /**
     * Present cards still must be drawable by the player.
     */
    @Test
    void chooseCardRejectsPresentButNotDrawableCard() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        Card card = new TestCard(30, false);
        context.board().upperCards.add(card);
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);
        context.draftState().registerDrawQuota(alice, 1, 0);

        assertThrows(CardNotDrawableException.class, () -> context.handler().chooseCard(alice, card.getId()));
    }

    /**
     * A drawable card that is neither a building nor a character is rejected
     * when the handler tries to apply it to the player.
     */
    @Test
    void chooseCardRejectsDrawableCardWithUnsupportedRuntimeType() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        Card card = new TestCard(31, true);
        context.board().upperCards.add(card);
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);
        context.draftState().registerDrawQuota(alice, 1, 0);

        assertThrows(CardNotDrawableException.class, () -> context.handler().chooseCard(alice, card.getId()));
    }

    /**
     * Standard draw is available only while the game is in progress.
     */
    @Test
    void chooseCardRejectsWrongGameStatus() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        context.game().setStatus(GameStatus.SETUP);
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);

        assertThrows(InvalidGameStatusException.class, () -> context.handler().chooseCard(alice, 1));
    }

    /**
     * Standard draw is available only during PLAYER_DRAWS.
     */
    @Test
    void chooseCardRejectsWrongRoundPhase() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLACING_TOTEMS);

        assertThrows(InvalidGamePhaseException.class, () -> context.handler().chooseCard(alice, 1));
    }

    /**
     * Only the current player can choose a standard card.
     */
    @Test
    void chooseCardRejectsPlayerThatIsNotCurrent() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        TestContext context = context(List.of(alice, bob));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice, bob));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);

        assertThrows(NotPlayersTurnException.class, () -> context.handler().chooseCard(bob, 1));
    }

    /**
     * A valid extra draw removes an upper-row card, applies it to the player,
     * and moves the round to effect resolution.
     */
    @Test
    void extraDrawDrawsUpperCardAndMovesToResolvingEffects() {
        Player alice = playerWithExtraDraw("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        Hunter hunter = hunter(12);
        context.board().upperCards.add(hunter);
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.EXTRA_DRAWS);

        context.handler().extraDrawn(alice, hunter.getId());

        assertTrue(context.board().upperCards.isEmpty());
        assertEquals(1, alice.getTribe().getCharactersSize());
        assertEquals(RoundPhase.RESOLVING_EFFECTS, context.roundFlow().getCurrentRoundPhase());
    }

    @Test
    void SkipextraDrawDrawsUpperCardAndMovesToResolvingEffects() {
        Player alice = playerWithExtraDraw("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        Hunter hunter = hunter(12);
        context.board().upperCards.add(hunter);
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.EXTRA_DRAWS);

        context.handler().SkipExtraDrawn(alice);

        assertTrue(context.board().upperCards.contains(hunter));
        assertEquals(0, alice.getTribe().getCharactersSize());
        assertEquals(RoundPhase.RESOLVING_EFFECTS, context.roundFlow().getCurrentRoundPhase());
    }

    /**
     * Extra draw only accepts upper-row cards.
     */
    @Test
    void extraDrawRejectsLowerRowCardAsNotPresentForExtraDraw() {
        Player alice = playerWithExtraDraw("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        Hunter hunter = hunter(14);
        context.board().lowerCards.add(hunter);
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.EXTRA_DRAWS);

        assertThrows(CardNotPresentException.class, () -> context.handler().extraDrawn(alice, hunter.getId()));
    }

    /**
     * When standard draws finish and a next-round player owns a useful extra
     * draw, the handler enters EXTRA_DRAWS for that player.
     */
    @Test
    void completedStandardDraftStartsExtraDrawForEligiblePlayer() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = playerWithExtraDraw("Bob", TotemColor.BLUE);
        TestContext context = context(List.of(alice, bob));
        Hunter aliceCard = hunter(15);
        Hunter bobExtraCard = hunter(16);
        context.board().upperCards.add(aliceCard);
        context.board().upperCards.add(bobExtraCard);
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);
        context.draftState().registerDrawQuota(alice, 1, 0);

        context.handler().chooseCard(alice, aliceCard.getId());

        assertEquals(RoundPhase.EXTRA_DRAWS, context.roundFlow().getCurrentRoundPhase());
        assertEquals(bob, context.roundFlow().getCurrentPlayer());
    }

    /**
     * A current player disconnecting during standard draft is returned to the
     * last starting tile and the flow moves on.
     */
    @Test
    void currentPlayerDisconnectionDuringDraftSkipsThatPlayer() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        TestContext context = context(List.of(alice, bob));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice, bob));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);
        context.draftState().registerDrawQuota(alice, 1, 0);
        context.draftState().registerDrawQuota(bob, 0, 0);

        context.handler().handlePlayerDisconnection(alice);

        assertEquals(List.of(alice), context.board().movedToLastStartingTile);
        assertEquals(RoundPhase.RESOLVING_EFFECTS, context.roundFlow().getCurrentRoundPhase());
    }

    /**
     * Disconnecting the current extra-draw player skips the optional action and
     * moves to effect resolution.
     */
    @Test
    void currentExtraDrawPlayerDisconnectionMovesToResolvingEffects() {
        Player alice = playerWithExtraDraw("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.EXTRA_DRAWS);

        context.handler().handleExtraDrawPlayerDisconnection(alice);

        assertEquals(RoundPhase.RESOLVING_EFFECTS, context.roundFlow().getCurrentRoundPhase());
    }

    /**
     * Extra draw requires the player to own the effect that grants it.
     */
    @Test
    void extraDrawRejectsPlayerWithoutExtraDrawBuilding() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.EXTRA_DRAWS);

        assertThrows(IllegalStateException.class, () -> context.handler().extraDrawn(alice, 1));
    }

    /**
     * Extra draw follows the same status, phase, and current-player validation
     * as other player actions.
     */
    @Test
    void extraDrawRejectsWrongStatusPhaseAndPlayer() {
        Player alice = playerWithExtraDraw("Alice", TotemColor.RED);
        Player bob = playerWithExtraDraw("Bob", TotemColor.BLUE);
        TestContext context = context(List.of(alice, bob));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice, bob));

        context.game().setStatus(GameStatus.SETUP);
        context.roundFlow().setCurrentRoundPhase(RoundPhase.EXTRA_DRAWS);
        assertThrows(InvalidGameStatusException.class, () -> context.handler().extraDrawn(alice, 1));

        context.game().setStatus(GameStatus.IN_PROGRESS);
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);
        assertThrows(InvalidGamePhaseException.class, () -> context.handler().extraDrawn(alice, 1));

        context.roundFlow().setCurrentRoundPhase(RoundPhase.EXTRA_DRAWS);
        assertThrows(NotPlayersTurnException.class, () -> context.handler().extraDrawn(bob, 1));
    }

    /**
     * When the next draft player has no useful drawable card, their turn is
     * closed and standard drafting ends.
     */
    @Test
    void completedDrawSkipsNextPlayerWithoutUsefulDraw() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        TestContext context = context(List.of(alice, bob));
        Hunter aliceCard = hunter(17);
        context.board().upperCards.add(aliceCard);
        context.roundFlow().setCurrentPhaseOrder(List.of(alice, bob));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);
        context.draftState().registerDrawQuota(alice, 1, 0);
        context.draftState().registerDrawQuota(bob, 1, 0);

        context.handler().chooseCard(alice, aliceCard.getId());

        assertEquals(List.of(alice, bob), context.board().returnedToStartingTile);
        assertEquals(RoundPhase.RESOLVING_EFFECTS, context.roundFlow().getCurrentRoundPhase());
    }

    /**
     * A disconnected player reached by the draft cursor is moved to the last
     * starting tile and skipped.
     */
    @Test
    void completedDrawSkipsNextDisconnectedPlayer() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        TestContext context = context(List.of(alice, bob));
        Hunter aliceCard = hunter(18);
        Hunter remainingCard = hunter(19);
        context.board().upperCards.add(aliceCard);
        context.board().upperCards.add(remainingCard);
        context.roundFlow().setCurrentPhaseOrder(List.of(alice, bob));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);
        context.draftState().registerDrawQuota(alice, 1, 0);
        context.draftState().registerDrawQuota(bob, 1, 0);
        context.game().getConnectedPlayers().put(bob, false);

        context.handler().chooseCard(alice, aliceCard.getId());

        assertEquals(List.of(bob), context.board().movedToLastStartingTile);
        assertEquals(RoundPhase.RESOLVING_EFFECTS, context.roundFlow().getCurrentRoundPhase());
    }

    /**
     * The handler requires all collaborators.
     */
    @Test
    void constructorRejectsMissingCollaborators() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));

        assertThrows(IllegalArgumentException.class, () -> new CardDraftHandler(null, context.board(), context.roundFlow(), context.draftState()));
        assertThrows(IllegalArgumentException.class, () -> new CardDraftHandler(context.game(), null, context.roundFlow(), context.draftState()));
        assertThrows(IllegalArgumentException.class, () -> new CardDraftHandler(context.game(), context.board(), null, context.draftState()));
        assertThrows(IllegalArgumentException.class, () -> new CardDraftHandler(context.game(), context.board(), context.roundFlow(), null));
    }

    private static TestContext context(List<Player> players) {
        RecordingBoard board = new RecordingBoard(players);
        Game game = new Game(1, players, board);
        game.setStatus(GameStatus.IN_PROGRESS);
        RoundFlow roundFlow = game.getRoundFlow();
        PlayerDraftState draftState = game.getPlayerDraftState();
        CardDraftHandler handler = new CardDraftHandler(game, board, roundFlow, draftState);
        return new TestContext(game, board, roundFlow, draftState, handler);
    }

    private static Player player(String nickname, TotemColor color) {
        return new Player(nickname, 0, 0, color);
    }

    private static Player playerWithExtraDraw(String nickname, TotemColor color) {
        return new Player(nickname, 0, 0, color, new ExtraDrawTribe());
    }

    private static Hunter hunter(int id) {
        return new Hunter(id, Era.first, false, TribeCardType.HUNTER);
    }

    private record TestContext(Game game,
                               RecordingBoard board,
                               RoundFlow roundFlow,
                               PlayerDraftState draftState,
                               CardDraftHandler handler) {
    }

    private static class ExtraDrawTribe extends Tribe {
        @Override
        public boolean hasEndRoundExtraCardBuilding() {
            return true;
        }
    }

    private static class RecordingBoard extends Board {
        private final List<Player> nextRoundOrder;
        private final List<Card> upperCards = new ArrayList<>();
        private final List<Card> lowerCards = new ArrayList<>();
        private final List<Player> returnedToStartingTile = new ArrayList<>();
        private final List<Player> movedToLastStartingTile = new ArrayList<>();

        RecordingBoard(List<Player> players) {
            super(
                    TribeDeck.fromOrderedCards(new ArrayList<Card>()),
                    BuildingDeck.fromOrderedCards(new ArrayList<Card>()),
                    players
            );
            this.nextRoundOrder = new ArrayList<>(players);
        }

        @Override
        public Card findCardById(int cardId) {
            return this.getAllCards().stream()
                    .filter(card -> card.getId() == cardId)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public CardRow getCardRow(Card card) {
            if (this.upperCards.contains(card)) {
                return CardRow.UPPER;
            }
            if (this.lowerCards.contains(card)) {
                return CardRow.LOWER;
            }
            return CardRow.NOT_PRESENT;
        }

        @Override
        public boolean canPlayerDrawCard(Player player, Card card) {
            return this.getCardRow(card) != CardRow.NOT_PRESENT && card.isDrawable(player);
        }

        @Override
        public void removeUpperCard(Card card) {
            this.upperCards.remove(card);
        }

        @Override
        public void removeLowerCard(Card card) {
            this.lowerCards.remove(card);
        }

        @Override
        public List<Card> getDraftableUpperCards(Player player) {
            return this.upperCards.stream().filter(card -> card.isDrawable(player)).toList();
        }

        @Override
        public List<Card> getDraftableLowerCards(Player player) {
            return this.lowerCards.stream().filter(card -> card.isDrawable(player)).toList();
        }

        @Override
        public void returnPlayerToStartingTile(Player player) {
            this.returnedToStartingTile.add(player);
        }

        @Override
        public void movePlayerToLastStartingTile(Player player) {
            this.movedToLastStartingTile.add(player);
        }

        @Override
        public List<Player> getNextRoundTurnOrder() {
            return new ArrayList<>(this.nextRoundOrder);
        }

        private List<Card> getAllCards() {
            List<Card> cards = new ArrayList<>(this.upperCards);
            cards.addAll(this.lowerCards);
            return cards;
        }
    }

    private static class TestCard extends Card {
        private final boolean drawable;

        TestCard(int id, boolean drawable) {
            super(id, Era.first);
            this.drawable = drawable;
        }

        @Override
        public boolean isDrawable(Player player) {
            return this.drawable;
        }
    }
}
