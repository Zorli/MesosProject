package it.polimi.ingsoft.mesos.server.model.game.game;

import it.polimi.ingsoft.mesos.server.model.game.board.Board;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.Card;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.game.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.game.enums.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidGamePhaseException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.InvalidGameStatusException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.NotPlayersTurnException;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.TileNotAvailableException;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TotemPlacementHandler}.
 *
 * <p>The handler is tested with a small recording board so the assertions stay
 * focused on the handler contract: validate the request, place the player,
 * register draw quotas, advance turn flow, and enter the draw phase after the
 * last totem.
 */
class TotemPlacementHandlerTest {

    /**
     * A valid non-last totem placement updates the board, stores the draw quota,
     * advances to the next player, and marks the game changed.
     */
    @Test
    void validTotemPlacementRegistersQuotaAndAdvancesToNextPlayer() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        TestContext context = context(List.of(alice, bob));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice, bob));

        context.handler().moveTotem(alice, "B");

        assertEquals(List.of("Alice:B"), context.board().placements);
        assertEquals(1, context.draftState().getRemainingUpperChoices(alice));
        assertEquals(2, context.draftState().getRemainingLowerChoices(alice));
        assertEquals(bob, context.roundFlow().getCurrentPlayer());
        assertEquals(1L, context.game().getStateVersion());
    }

    /**
     * When the last player places a totem, the handler prepares the standard
     * card-draw phase using the board resolution order.
     */
    @Test
    void lastTotemPlacementStartsPlayerDrawPhase() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.board().resolutionOrder = new ArrayList<>(List.of(alice));

        context.handler().moveTotem(alice, "C");

        assertTrue(context.board().shiftedToLastStartingTile);
        assertTrue(context.board().resolvedEventA);
        assertEquals(RoundPhase.PLAYER_DRAWS, context.roundFlow().getCurrentRoundPhase());
        assertEquals(List.of(alice), context.roundFlow().getCurrentPhaseOrder());
    }

    /**
     * If the current player disconnects during totem placement, the handler
     * skips them by advancing the current phase order.
     */
    @Test
    void currentPlayerDisconnectionAdvancesToNextTotemPlayer() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        TestContext context = context(List.of(alice, bob));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice, bob));
        context.game().getConnectedPlayers().put(alice, false);

        context.handler().handlePlayerDisconnection(alice);

        assertEquals(bob, context.roundFlow().getCurrentPlayer());
    }

    /**
     * Disconnecting a player whose totem-placement turn was already processed
     * does not move the cursor backwards or change the board.
     */
    @Test
    void pastPlayerDisconnectionDoesNotChangeCurrentTotemPlayer() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        Player carol = player("Carol", TotemColor.WHITE);
        TestContext context = context(List.of(alice, bob, carol));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice, bob, carol));
        context.roundFlow().advanceToNextPlayer();

        context.handler().handlePlayerDisconnection(alice);

        assertEquals(bob, context.roundFlow().getCurrentPlayer());
    }

    /**
     * When the resolution order contains only disconnected players, the handler
     * skips the draft phase and moves directly to board reset.
     */
    @Test
    void lastTotemPlacementWithNoConnectedDraftPlayersMovesToResettingBoard() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.board().resolutionOrder = new ArrayList<>(List.of(alice));
        context.game().getConnectedPlayers().put(alice, false);

        context.handler().moveTotem(alice, "B");

        assertEquals(RoundPhase.RESETTING_BOARD, context.roundFlow().getCurrentRoundPhase());
        assertEquals(List.of(alice), context.board().movedToLastStartingTile);
    }

    /**
     * The handler rejects actions before the game is in progress.
     */
    @Test
    void moveTotemRejectsWrongGameStatus() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        context.game().setStatus(GameStatus.SETUP);
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));

        assertThrows(InvalidGameStatusException.class, () -> context.handler().moveTotem(alice, "B"));
    }

    /**
     * Totem placement is legal only in the placing-totems phase.
     */
    @Test
    void moveTotemRejectsWrongRoundPhase() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);

        assertThrows(InvalidGamePhaseException.class, () -> context.handler().moveTotem(alice, "B"));
    }

    /**
     * Only the current player in the phase order can place a totem.
     */
    @Test
    void moveTotemRejectsPlayerThatIsNotCurrent() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        TestContext context = context(List.of(alice, bob));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice, bob));

        assertThrows(NotPlayersTurnException.class, () -> context.handler().moveTotem(bob, "B"));
    }

    /**
     * Missing, null, or occupied spaces are rejected before the board placement
     * is applied.
     */
    @Test
    void moveTotemRejectsUnavailableTile() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.board().spaceAvailable = false;

        assertThrows(TileNotAvailableException.class, () -> context.handler().moveTotem(alice, "B"));
        assertThrows(TileNotAvailableException.class, () -> context.handler().moveTotem(alice, null));
        assertTrue(context.board().placements.isEmpty());
    }

    /**
     * Null collaborators are rejected by the constructor because the handler
     * cannot enforce rules without the aggregate, board, flow, and draft state.
     */
    @Test
    void constructorRejectsMissingCollaborators() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));

        assertThrows(IllegalArgumentException.class, () -> new TotemPlacementHandler(null, context.board(), context.roundFlow(), context.draftState()));
        assertThrows(IllegalArgumentException.class, () -> new TotemPlacementHandler(context.game(), null, context.roundFlow(), context.draftState()));
        assertThrows(IllegalArgumentException.class, () -> new TotemPlacementHandler(context.game(), context.board(), null, context.draftState()));
        assertThrows(IllegalArgumentException.class, () -> new TotemPlacementHandler(context.game(), context.board(), context.roundFlow(), null));
    }

    private static TestContext context(List<Player> players) {
        RecordingBoard board = new RecordingBoard(players);
        Game game = new Game(1, players, board);
        game.setStatus(GameStatus.IN_PROGRESS);
        RoundFlow roundFlow = game.getRoundFlow();
        PlayerDraftState draftState = game.getPlayerDraftState();
        TotemPlacementHandler handler = new TotemPlacementHandler(game, board, roundFlow, draftState);
        return new TestContext(game, board, roundFlow, draftState, handler);
    }

    private static Player player(String nickname, TotemColor color) {
        return new Player(nickname, 0, 0, color);
    }

    private record TestContext(Game game,
                               RecordingBoard board,
                               RoundFlow roundFlow,
                               PlayerDraftState draftState,
                               TotemPlacementHandler handler) {
    }

    private static class RecordingBoard extends Board {
        private boolean spaceAvailable = true;
        private boolean shiftedToLastStartingTile;
        private boolean resolvedEventA;
        private List<Player> resolutionOrder = new ArrayList<>();
        private final List<String> placements = new ArrayList<>();
        private final List<Player> movedToLastStartingTile = new ArrayList<>();

        RecordingBoard(List<Player> players) {
            super(
                    TribeDeck.fromOrderedCards(new ArrayList<Card>()),
                    BuildingDeck.fromOrderedCards(new ArrayList<Card>()),
                    players
            );
        }

        @Override
        public boolean isSpaceAvailable(String targetCode) {
            return this.spaceAvailable;
        }

        @Override
        public void placePlayerOnOfferSpace(Player player, String targetCode) {
            this.placements.add(player.getNickname() + ":" + targetCode);
        }

        @Override
        public int[] returnNumberDrawableCard(Player player) {
            return new int[]{1, 2};
        }

        @Override
        public void shiftToLastStartingTile() {
            this.shiftedToLastStartingTile = true;
        }

        @Override
        public void resolveEventA() {
            this.resolvedEventA = true;
        }

        @Override
        public List<Player> getResolutionOrder() {
            return new ArrayList<>(this.resolutionOrder);
        }

        @Override
        public void movePlayerToLastStartingTile(Player player) {
            this.movedToLastStartingTile.add(player);
        }
    }
}
