package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.game.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.game.PlayerDraftState;
import it.polimi.ingsoft.mesos.server.model.game.game.enums.DraftTurnState;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PlayerDraftState}.
 *
 * <p>The class stores only standard draw quotas and the current draft-turn
 * state. The tests intentionally avoid board and card objects, except for the
 * row enum needed to consume a quota.
 */
class PlayerDraftStateTest {

    /**
     * A fresh draft state represents "no active card choice" and no registered
     * quota for any player.
     */
    @Test
    void newDraftStateStartsCompletedWithNoRemainingChoices() {
        Player alice = player("Alice");
        PlayerDraftState draftState = new PlayerDraftState();

        assertEquals(DraftTurnState.TURN_COMPLETED, draftState.getPhase());
        assertTrue(draftState.isTurnCompleted());
        assertFalse(draftState.isWaitingForCardSelection());
        assertEquals(0, draftState.getRemainingUpperChoices(alice));
        assertEquals(0, draftState.getRemainingLowerChoices(alice));
        assertFalse(draftState.hasRemainingChoices(alice));
    }

    /**
     * Registering a quota stores both row counters. Zero is valid because a
     * player may be allowed to draw only from one row.
     */
    @Test
    void registerDrawQuotaStoresUpperAndLowerChoices() {
        Player alice = player("Alice");
        PlayerDraftState draftState = new PlayerDraftState();

        draftState.registerDrawQuota(alice, 2, 0);

        assertEquals(2, draftState.getRemainingUpperChoices(alice));
        assertEquals(0, draftState.getRemainingLowerChoices(alice));
        assertTrue(draftState.hasRemainingChoices(alice));
    }

    /**
     * Quota registration rejects invalid caller data before mutating the state.
     */
    @Test
    void registerDrawQuotaRejectsNullPlayerAndNegativeChoices() {
        Player alice = player("Alice");
        PlayerDraftState draftState = new PlayerDraftState();

        assertThrows(IllegalArgumentException.class, () -> draftState.registerDrawQuota(null, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> draftState.registerDrawQuota(alice, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> draftState.registerDrawQuota(alice, 0, -1));
    }

    /**
     * Card selection can start only after the player's row counters have been
     * registered. This prevents a draw turn from starting without board-derived
     * quotas.
     */
    @Test
    void startCardSelectionRequiresRegisteredQuota() {
        Player alice = player("Alice");
        PlayerDraftState draftState = new PlayerDraftState();

        assertThrows(IllegalStateException.class, () -> draftState.startCardSelection(alice));

        draftState.registerDrawQuota(alice, 1, 1);
        draftState.startCardSelection(alice);

        assertEquals(DraftTurnState.WAITING_FOR_CARD_SELECTION, draftState.getPhase());
        assertTrue(draftState.isWaitingForCardSelection());
    }

    /**
     * Consuming a choice decrements only the selected row. The draft turn stays
     * open while at least one quota remains.
     */
    @Test
    void consumeChoiceDecrementsOnlyTheSelectedRow() {
        Player alice = player("Alice");
        PlayerDraftState draftState = new PlayerDraftState();
        draftState.registerDrawQuota(alice, 1, 1);
        draftState.startCardSelection(alice);

        draftState.consumeChoice(alice, CardRow.UPPER);

        assertEquals(0, draftState.getRemainingUpperChoices(alice));
        assertEquals(1, draftState.getRemainingLowerChoices(alice));
        assertTrue(draftState.isWaitingForCardSelection());
    }

    /**
     * When the last remaining quota is consumed, the draft turn becomes
     * completed.
     */
    @Test
    void consumingLastChoiceCompletesTheTurn() {
        Player alice = player("Alice");
        PlayerDraftState draftState = new PlayerDraftState();
        draftState.registerDrawQuota(alice, 1, 0);
        draftState.startCardSelection(alice);

        draftState.consumeChoice(alice, CardRow.UPPER);

        assertEquals(0, draftState.getRemainingUpperChoices(alice));
        assertTrue(draftState.isTurnCompleted());
    }

    /**
     * A row can be consumed only while the draft state is waiting for a card and
     * the player has a positive quota for that row.
     */
    @Test
    void consumeChoiceRejectsRowsThatAreNotCurrentlyAllowed() {
        Player alice = player("Alice");
        PlayerDraftState draftState = new PlayerDraftState();
        draftState.registerDrawQuota(alice, 1, 0);

        assertThrows(IllegalStateException.class, () -> draftState.consumeChoice(alice, CardRow.UPPER));

        draftState.startCardSelection(alice);

        assertThrows(IllegalStateException.class, () -> draftState.consumeChoice(alice, CardRow.LOWER));
        assertThrows(IllegalArgumentException.class, () -> draftState.consumeChoice(alice, CardRow.NOT_PRESENT));
    }

    /**
     * Resetting one player removes only that player's quotas and completes the
     * active turn state.
     */
    @Test
    void resetForNextPlayerClearsOnlyThatPlayerQuota() {
        Player alice = player("Alice");
        Player bob = player("Bob");
        PlayerDraftState draftState = new PlayerDraftState();
        draftState.registerDrawQuota(alice, 1, 1);
        draftState.registerDrawQuota(bob, 2, 2);
        draftState.startCardSelection(alice);

        draftState.resetForNextPlayer(alice);

        assertEquals(0, draftState.getRemainingUpperChoices(alice));
        assertEquals(0, draftState.getRemainingLowerChoices(alice));
        assertEquals(2, draftState.getRemainingUpperChoices(bob));
        assertEquals(2, draftState.getRemainingLowerChoices(bob));
        assertTrue(draftState.isTurnCompleted());
    }

    /**
     * Resetting for a new round clears every quota and returns the state to
     * completed.
     */
    @Test
    void resetForNewRoundClearsAllQuotas() {
        Player alice = player("Alice");
        Player bob = player("Bob");
        PlayerDraftState draftState = new PlayerDraftState();
        draftState.registerDrawQuota(alice, 1, 1);
        draftState.registerDrawQuota(bob, 2, 2);
        draftState.startCardSelection(alice);

        draftState.resetForNewRound();

        assertEquals(0, draftState.getRemainingUpperChoices(alice));
        assertEquals(0, draftState.getRemainingLowerChoices(bob));
        assertTrue(draftState.getRemainingUpperChoicesByPlayer().isEmpty());
        assertTrue(draftState.getRemainingLowerChoicesByPlayer().isEmpty());
        assertTrue(draftState.isTurnCompleted());
    }

    /**
     * The quota maps returned by the getters are copies. Mutating them must not
     * mutate the draft state.
     */
    @Test
    void quotaMapGettersReturnCopies() {
        Player alice = player("Alice");
        PlayerDraftState draftState = new PlayerDraftState();
        draftState.registerDrawQuota(alice, 1, 2);

        Map<Player, Integer> upperChoices = draftState.getRemainingUpperChoicesByPlayer();
        Map<Player, Integer> lowerChoices = draftState.getRemainingLowerChoicesByPlayer();
        upperChoices.clear();
        lowerChoices.clear();

        assertEquals(1, draftState.getRemainingUpperChoices(alice));
        assertEquals(2, draftState.getRemainingLowerChoices(alice));
    }

    private static Player player(String nickname) {
        return new Player(nickname, 0, 0, TotemColor.RED);
    }
}
