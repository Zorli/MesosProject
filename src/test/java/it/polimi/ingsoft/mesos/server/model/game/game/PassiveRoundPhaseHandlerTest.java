package it.polimi.ingsoft.mesos.server.model.game.game;

import it.polimi.ingsoft.mesos.server.model.game.board.Board;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.Card;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.game.enums.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PassiveRoundPhaseHandler}.
 *
 * <p>The handler owns automatic phase progression. These tests use a recording
 * board to observe which passive board operations are invoked without relying
 * on real decks or event cards.
 */
class PassiveRoundPhaseHandlerTest {

    /**
     * In non-final rounds, resolving effects continues automatically through
     * board reset and stops when the next player-driven totem phase begins.
     */
    @Test
    void nonFinalResolvingEffectsAdvancesThroughResetToNextTotemPhase() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().advanceRoundNumber();
        context.roundFlow().setCurrentRoundPhase(RoundPhase.RESOLVING_EFFECTS);
        context.draftState().registerDrawQuota(alice, 1, 1);

        context.handler().advancePassivePhases();

        assertTrue(context.board().resolvedLowerEvents);
        assertTrue(context.board().resetAfterRound);
        assertEquals(RoundPhase.PLACING_TOTEMS, context.roundFlow().getCurrentRoundPhase());
        assertEquals(2, context.roundFlow().getRoundNumber());
        assertEquals(List.of(alice), context.roundFlow().getCurrentPhaseOrder());
        assertEquals(0, context.draftState().getRemainingUpperChoices(alice));
    }

    /**
     * If board reset reports an era change, the game era advances exactly once
     * as part of the automatic reset flow.
     */
    @Test
    void boardResetEraChangeAdvancesGameEra() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().advanceRoundNumber();
        context.roundFlow().setCurrentRoundPhase(RoundPhase.RESOLVING_EFFECTS);
        context.board().eraChangesOnReset = true;

        context.handler().advancePassivePhases();

        assertEquals(Era.second, context.game().getCurrentEra());
    }

    /**
     * In the final round, resolving effects leads immediately to final scoring,
     * and final scoring finishes the game.
     */
    @Test
    void finalRoundResolvingEffectsCalculatesFinalScoreAndFinishesGame() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        for (int i = 0; i < 10; i++) {
            context.roundFlow().advanceRoundNumber();
        }
        context.roundFlow().setCurrentRoundPhase(RoundPhase.RESOLVING_EFFECTS);

        context.handler().advancePassivePhases();

        assertTrue(context.board().resolvedAllEvents);
        assertEquals(GameStatus.FINISHED, context.game().getStatus());
    }

    /**
     * Passive advancement does nothing when the current phase is already a
     * player-driven phase.
     */
    @Test
    void playerDrivenPhaseDoesNotAdvanceAutomatically() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));
        context.roundFlow().setCurrentPhaseOrder(List.of(alice));
        context.roundFlow().advanceRoundNumber();
        context.roundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);

        context.handler().advancePassivePhases();

        assertEquals(RoundPhase.PLAYER_DRAWS, context.roundFlow().getCurrentRoundPhase());
        assertTrue(!context.board().resolvedLowerEvents);
        assertTrue(!context.board().resetAfterRound);
    }

    /**
     * The handler cannot operate without its four collaborators.
     */
    @Test
    void constructorRejectsMissingCollaborators() {
        Player alice = player("Alice", TotemColor.RED);
        TestContext context = context(List.of(alice));

        assertThrows(IllegalArgumentException.class, () -> new PassiveRoundPhaseHandler(null, context.board(), context.roundFlow(), context.draftState()));
        assertThrows(IllegalArgumentException.class, () -> new PassiveRoundPhaseHandler(context.game(), null, context.roundFlow(), context.draftState()));
        assertThrows(IllegalArgumentException.class, () -> new PassiveRoundPhaseHandler(context.game(), context.board(), null, context.draftState()));
        assertThrows(IllegalArgumentException.class, () -> new PassiveRoundPhaseHandler(context.game(), context.board(), context.roundFlow(), null));
    }

    private static TestContext context(List<Player> players) {
        RecordingBoard board = new RecordingBoard(players);
        Game game = new Game(1, players, board);
        game.setStatus(GameStatus.IN_PROGRESS);
        RoundFlow roundFlow = game.getRoundFlow();
        PlayerDraftState draftState = game.getPlayerDraftState();
        PassiveRoundPhaseHandler handler = new PassiveRoundPhaseHandler(game, board, roundFlow, draftState);
        return new TestContext(game, board, roundFlow, draftState, handler);
    }

    private static Player player(String nickname, TotemColor color) {
        return new Player(nickname, 0, 0, color);
    }

    private record TestContext(Game game,
                               RecordingBoard board,
                               RoundFlow roundFlow,
                               PlayerDraftState draftState,
                               PassiveRoundPhaseHandler handler) {
    }

    private static class RecordingBoard extends Board {
        private final List<Player> nextRoundOrder;
        private boolean resolvedLowerEvents;
        private boolean resolvedAllEvents;
        private boolean resetAfterRound;
        private boolean eraChangesOnReset;

        RecordingBoard(List<Player> players) {
            super(
                    TribeDeck.fromOrderedCards(new ArrayList<Card>()),
                    BuildingDeck.fromOrderedCards(new ArrayList<Card>()),
                    players
            );
            this.nextRoundOrder = new ArrayList<>(players);
        }

        @Override
        public void resolveLowerEvents() {
            this.resolvedLowerEvents = true;
        }

        @Override
        public void resolveAllEvents() {
            this.resolvedAllEvents = true;
        }

        @Override
        public boolean resetBoardAfterRound(Era currentEra) {
            this.resetAfterRound = true;
            return this.eraChangesOnReset;
        }

        @Override
        public List<Player> getNextRoundTurnOrder() {
            return new ArrayList<>(this.nextRoundOrder);
        }
    }
}
