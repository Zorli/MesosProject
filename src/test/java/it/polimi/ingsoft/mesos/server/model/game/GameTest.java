package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.game.board.Board;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.Card;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.game.enums.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.server.network.interfaces.Listener;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ServerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the observable contract of {@link Game}.
 *
 * <p>The tests stay at aggregate level: they verify lifecycle state,
 * connection state, setup effects, and listener notification behavior. Detailed
 * totem-placement and card-drafting rules are left to the dedicated handler and
 * board tests.
 */
class GameTest {

    /**
     * A newly created game starts in setup, first era, version zero, and with
     * every participant connected. This is the base state that the controller
     * receives before it enqueues the setup command.
     */
    @Test
    void newGameStartsInSetupWithAllPlayersConnected() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        Game game = game(alice, bob);

        assertEquals(1, game.getId());
        assertEquals(GameStatus.SETUP, game.getStatus());
        assertEquals(Era.first, game.getCurrentEra());
        assertEquals(0L, game.getStateVersion());
        assertTrue(game.getConnectedPlayers().get(alice));
        assertTrue(game.getConnectedPlayers().get(bob));
    }

    /**
     * Setup is the transition from a created match to a playable match. It
     * initializes round flow and applies the starting food bonus based only on
     * player order.
     */
    @Test
    void setupStartsFirstRoundAndAssignsInitialFoodByPlayerOrder() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        Player carol = player("Carol", TotemColor.WHITE);
        Game game = game(alice, bob, carol);

        game.setup();

        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertEquals(1, game.getRoundFlow().getRoundNumber());
        assertEquals(RoundPhase.PLACING_TOTEMS, game.getRoundFlow().getCurrentRoundPhase());
        assertEquals(List.of(alice, bob, carol), game.getRoundFlow().getCurrentPhaseOrder());
        assertEquals(2, alice.getFood());
        assertEquals(3, bob.getFood());
        assertEquals(3, carol.getFood());
    }

    /**
     * The fourth and later players receive the larger initial food bonus. This
     * keeps the player-order based setup rule explicit.
     */
    @Test
    void setupAssignsFourFoodToFourthPlayer() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        Player carol = player("Carol", TotemColor.WHITE);
        Player dave = player("Dave", TotemColor.BLACK);
        Game game = game(alice, bob, carol, dave);

        game.setup();

        assertEquals(4, dave.getFood());
    }

    /**
     * If every player is disconnected, the aggregate reaches a terminal state:
     * there is no remaining connected participant able to continue the match.
     */
    @Test
    void disconnectingEveryPlayerFinishesTheGame() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        Game game = game(alice, bob);

        game.disconnectedPlayer(alice);
        game.disconnectedPlayer(bob);

        assertEquals(GameStatus.FINISHED, game.getStatus());
    }

    /**
     * With exactly one connected player, normal player actions are rejected and
     * reported as an error payload. The model state remains in the last-player
     * waiting condition used by the controller timeout.
     */
    @Test
    void normalActionWithOnlyOneConnectedPlayerReportsConnectionError() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        Game game = game(alice, bob);
        RecordingListener listener = new RecordingListener();
        game.addListener(listener);
        game.disconnectedPlayer(bob);
        listener.receivedPayloads.clear();

        game.moveTotem(alice, "B");

        ErrorPayload error = (ErrorPayload) listener.receivedPayloads.getFirst();
        assertEquals("InvalidConnectionState", error.code);
        assertEquals("Invalid connection state: only one connected", error.message);
        assertTrue(game.getStartTimer());
    }

    /**
     * The same one-connected-player protection applies to standard card draws.
     */
    @Test
    void chooseCardWithOnlyOneConnectedPlayerReportsConnectionError() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        Game game = game(alice, bob);
        RecordingListener listener = new RecordingListener();
        game.addListener(listener);
        game.disconnectedPlayer(bob);
        listener.receivedPayloads.clear();

        game.chooseCard(alice, 1);

        ErrorPayload error = (ErrorPayload) listener.receivedPayloads.getFirst();
        assertEquals("InvalidConnectionState", error.code);
    }

    /**
     * The same one-connected-player protection applies to extra draws.
     */
    @Test
    void extraDrawWithOnlyOneConnectedPlayerReportsConnectionError() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        Game game = game(alice, bob);
        RecordingListener listener = new RecordingListener();
        game.addListener(listener);
        game.disconnectedPlayer(bob);
        listener.receivedPayloads.clear();

        game.extraDrawn(alice, 1);

        ErrorPayload error = (ErrorPayload) listener.receivedPayloads.getFirst();
        assertEquals("InvalidConnectionState", error.code);
    }

    /**
     * During an active totem-placement phase, disconnecting the current player
     * lets the phase flow skip to the next connected player.
     */
    @Test
    void disconnectingCurrentPlayerDuringTotemPlacementAdvancesTheTurn() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        Game game = game(alice, bob);
        game.setup();

        game.disconnectedPlayer(alice);

        assertEquals(bob, game.getRoundFlow().getCurrentPlayer());
        assertFalse(game.getConnectedPlayers().get(alice));
    }

    /**
     * Calling setup outside SETUP does not throw to the caller. It is converted
     * into an error payload through the action executor.
     */
    @Test
    void setupCalledTwiceReportsStatusError() {
        Game game = game(player("Alice", TotemColor.RED), player("Bob", TotemColor.BLUE));
        RecordingListener listener = new RecordingListener();
        game.addListener(listener);
        game.setup();
        listener.receivedPayloads.clear();

        game.setup();

        ErrorPayload error = (ErrorPayload) listener.receivedPayloads.getFirst();
        assertEquals("InvalidGameStatusException", error.code);
    }

    /**
     * Error helpers expose client-visible error payloads without requiring a
     * full game action.
     */
    @Test
    void notifyErrorAndReportPhaseErrorDeliverErrorPayloads() {
        Game game = game(player("Alice", TotemColor.RED), player("Bob", TotemColor.BLUE));
        RecordingListener globalListener = new RecordingListener();
        RecordingListener targetListener = new RecordingListener();
        game.addListener(globalListener);

        game.notifyError("CODE", "message");
        game.reportPhaseError(targetListener);
        game.reportPhaseError(null);

        ErrorPayload globalError = (ErrorPayload) globalListener.receivedPayloads.getFirst();
        ErrorPayload targetError = (ErrorPayload) targetListener.receivedPayloads.getFirst();
        assertEquals("CODE", globalError.code);
        assertEquals("message", globalError.message);
        assertEquals("GAME_ACTION_ERROR", targetError.code);
        assertEquals(1, targetListener.receivedPayloads.size());
    }

    /**
     * Adding the same listener twice must not duplicate notifications. The
     * observable result is that one state-changing action produces one payload
     * for that listener.
     */
    @Test
    void duplicateListenerReceivesOnlyOneNotification() {
        Game game = game(player("Alice", TotemColor.RED), player("Bob", TotemColor.BLUE));
        RecordingListener listener = new RecordingListener();

        game.addListener(listener);
        game.addListener(listener);
        game.closeGame();

        assertEquals(1, listener.receivedPayloads.size());
    }

    /**
     * Removing a listener means later game updates are no longer delivered to
     * it. This verifies the externally visible effect of {@link Game#removeListener}.
     */
    @Test
    void removedListenerDoesNotReceiveLaterNotifications() {
        Game game = game(player("Alice", TotemColor.RED), player("Bob", TotemColor.BLUE));
        RecordingListener listener = new RecordingListener();

        game.addListener(listener);
        game.removeListener(listener);
        game.closeGame();

        assertTrue(listener.receivedPayloads.isEmpty());
    }

    /**
     * If all connected draft players have no useful standard draw, the last
     * totem placement can still stop in EXTRA_DRAWS when an extra draw is useful.
     */
    @Test
    void lastTotemPlacementSkipsPlayersWithoutUsefulStandardDrawAndStartsExtraDraw() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        alice.getTribe().addBuilding(new BuildingCard(1, Era.first, 0, 0, BenefitType.END_ROUND_EXTRA_CARD));
        RecordingBoard board = new RecordingBoard(List.of(alice, bob));
        board.resolutionOrder = new ArrayList<>(List.of(alice, bob));
        board.upperCards.add(new TestCard(2, alice));
        Game game = gameWithBoard(board, alice, bob);

        game.moveTotem(alice, "B");
        game.moveTotem(bob, "C");

        assertEquals(RoundPhase.EXTRA_DRAWS, game.getRoundFlow().getCurrentRoundPhase());
        assertEquals(alice, game.getRoundFlow().getCurrentPlayer());
        assertEquals(List.of(alice, bob), board.returnedToStartingTile);
        assertFalse(board.resetAfterRound);
    }

    /**
     * If all connected draft players have no useful standard draw and no useful
     * extra draw exists, the last totem placement reaches the next board reset.
     */
    @Test
    void lastTotemPlacementSkipsPlayersWithoutUsefulStandardDrawAndResetsBoard() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        RecordingBoard board = new RecordingBoard(List.of(alice, bob));
        board.resolutionOrder = new ArrayList<>(List.of(alice, bob));
        Game game = gameWithBoard(board, alice, bob);

        game.moveTotem(alice, "B");
        game.moveTotem(bob, "C");

        assertTrue(board.resolvedLowerEvents);
        assertTrue(board.resetAfterRound);
        assertEquals(RoundPhase.PLACING_TOTEMS, game.getRoundFlow().getCurrentRoundPhase());
        assertEquals(List.of(alice, bob), game.getRoundFlow().getCurrentPhaseOrder());
    }

    private static Game game(Player... players) {
        return DefaultGameFactory.getInstance().createGame(1, List.of(players));
    }

    private static Game gameWithBoard(Board board, Player... players) {
        Game game = new Game(1, List.of(players), board);
        game.setStatus(GameStatus.IN_PROGRESS);
        game.getRoundFlow().advanceRoundNumber();
        game.getRoundFlow().setCurrentRoundPhase(RoundPhase.PLACING_TOTEMS);
        game.getRoundFlow().setCurrentPhaseOrder(List.of(players));
        return game;
    }

    private static Player player(String nickname, TotemColor color) {
        return new Player(nickname, 0, 0, color);
    }

    private static class RecordingListener implements Listener {
        private final List<Object> receivedPayloads = new ArrayList<>();

        @Override
        public void notifyUpdate(ServerPayload payload) {
            this.receivedPayloads.add(payload);
        }
    }

    private static class RecordingBoard extends Board {
        private List<Player> resolutionOrder = new ArrayList<>();
        private final List<Card> upperCards = new ArrayList<>();
        private final List<Player> returnedToStartingTile = new ArrayList<>();
        private final List<Player> nextRoundOrder = new ArrayList<>();
        private boolean resolvedLowerEvents;
        private boolean resetAfterRound;

        RecordingBoard(List<Player> players) {
            super(
                    TribeDeck.fromOrderedCards(new ArrayList<Card>()),
                    BuildingDeck.fromOrderedCards(new ArrayList<Card>()),
                    players
            );
        }

        @Override
        public boolean isSpaceAvailable(String targetCode) {
            return true;
        }

        @Override
        public void placePlayerOnOfferSpace(Player player, String targetCode) {
        }

        @Override
        public int[] returnNumberDrawableCard(Player player) {
            return new int[]{0, 1};
        }

        @Override
        public void shiftToLastStartingTile() {
        }

        @Override
        public void resolveEventA() {
        }

        @Override
        public List<Player> getResolutionOrder() {
            return new ArrayList<>(this.resolutionOrder);
        }

        @Override
        public List<Card> getDraftableUpperCards(Player player) {
            return this.upperCards.stream().filter(card -> card.isDrawable(player)).toList();
        }

        @Override
        public List<Card> getDraftableLowerCards(Player player) {
            return List.of();
        }

        @Override
        public void returnPlayerToStartingTile(Player player) {
            this.returnedToStartingTile.add(player);
            this.nextRoundOrder.add(player);
        }

        @Override
        public void movePlayerToLastStartingTile(Player player) {
            this.nextRoundOrder.add(player);
        }

        @Override
        public List<Player> getNextRoundTurnOrder() {
            return new ArrayList<>(this.nextRoundOrder);
        }

        @Override
        public void resolveLowerEvents() {
            this.resolvedLowerEvents = true;
        }

        @Override
        public boolean resetBoardAfterRound(Era currentEra) {
            this.resetAfterRound = true;
            return false;
        }
    }

    private static class TestCard extends Card {
        private final Player drawablePlayer;

        TestCard(int id, Player drawablePlayer) {
            super(id, Era.first);
            this.drawablePlayer = drawablePlayer;
        }

        @Override
        public boolean isDrawable(Player player) {
            return this.drawablePlayer.equals(player);
        }
    }
}
