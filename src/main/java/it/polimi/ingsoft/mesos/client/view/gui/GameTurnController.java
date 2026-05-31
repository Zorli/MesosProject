package it.polimi.ingsoft.mesos.client.view.gui;

import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxy;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.OfferSpacePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.TurnOrderPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.TurnStatePayload;
import javafx.scene.control.Label;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns the local game-turn state and the rules that decide whether a click on
 * a market card or an offer-path tile is legal before the next preLobby update
 * arrives.
 *
 * <p>The preLobby is authoritative, but the GUI keeps a small local counter to
 * avoid letting the same player click too many cards in a row. When the preLobby
 * rejects an action with an error the controller rolls back the optimistic
 * counter via {@link #rollbackPendingDraftChoice()}.</p>
 */
final class GameTurnController {

    private static final String PHASE_PLACING_TOTEMS = "PLACING_TOTEMS";
    private static final String PHASE_PLAYER_DRAWS = "PLAYER_DRAWS";
    private static final String PHASE_EXTRA_DRAWS = "EXTRA_DRAWS";
    private static final String ROW_UPPER = "UPPER";
    private static final String ROW_LOWER = "LOWER";

    private final Label selectedCardLabel;
    private final Supplier<GamePayload> gameSupplier;
    private final Supplier<String> nicknameSupplier;
    private final ServerProxy serverProxy;
    private final Consumer<Runnable> serverDispatcher;
    private final Consumer<BoardPayload> boardRefresher;

    private String localDraftTurnKey;
    private int localUpperChoicesUsed;
    private int localLowerChoicesUsed;
    private String pendingDraftRow;

    GameTurnController(
            Label selectedCardLabel,
            Supplier<GamePayload> gameSupplier,
            Supplier<String> nicknameSupplier,
            ServerProxy serverProxy,
            Consumer<Runnable> serverDispatcher,
            Consumer<BoardPayload> boardRefresher
    ) {
        this.selectedCardLabel = selectedCardLabel;
        this.gameSupplier = gameSupplier;
        this.nicknameSupplier = nicknameSupplier;
        this.serverProxy = serverProxy;
        this.serverDispatcher = serverDispatcher;
        this.boardRefresher = boardRefresher;
    }

    // ---------------------------------------------------------------------
    // Public actions wired by GUIView and BoardRenderer
    // ---------------------------------------------------------------------

    /** Handles a card click from either market row and sends the correct command for the phase. */
    void chooseCardFromBoard(int cardId, String row) {
        if (!this.isCurrentUsersTurn()) {
            this.selectedCardLabel.setText("Wait for your turn: " + this.currentTurnPlayerName() + " is playing.");
            return;
        }
        if (this.isRoundPhase(PHASE_EXTRA_DRAWS)) {
            this.selectedCardLabel.setText("Extra draw requested for card " + cardId + ".");
            this.serverDispatcher.accept(() -> this.serverProxy.extraDraw(cardId));
            return;
        }
        if (this.isRoundPhase(PHASE_PLAYER_DRAWS)) {
            this.registerLocalCardChoice(row);
            this.pendingDraftRow = row;
            this.selectedCardLabel.setText("Card " + cardId + " selected.");
            GamePayload payload = this.gameSupplier.get();
            if (payload != null) {
                this.boardRefresher.accept(payload.board);
                this.updateActionHint();
            }
            this.serverDispatcher.accept(() -> this.serverProxy.chooseCard(cardId));
            return;
        }
        this.updateActionHint();
    }

    /** Sends a totem move when the clicked offer-path tile is legal for the current phase. */
    void moveTotemFromBoard(String tile) {
        if (!this.isCurrentUsersTurn()) {
            this.selectedCardLabel.setText("Wait for your turn: " + this.currentTurnPlayerName() + " is playing.");
            return;
        }
        if (!this.isRoundPhase(PHASE_PLACING_TOTEMS)) {
            this.updateActionHint();
            return;
        }
        this.selectedCardLabel.setText("Totem sent to " + tile + ".");
        this.serverDispatcher.accept(() -> this.serverProxy.moveTotem(tile));
    }

    /** Returns whether the current player can select another card from the given row. */
    boolean canChooseFromRow(String row) {
        if (!this.isCurrentUsersTurn()) {
            return false;
        }
        if (this.isRoundPhase(PHASE_EXTRA_DRAWS)) {
            return true;
        }
        if (!this.isRoundPhase(PHASE_PLAYER_DRAWS)) {
            return false;
        }
        OfferSpacePayload currentOffer = this.currentPlayersOfferSpace();
        if (currentOffer == null) {
            return true;
        }
        if (ROW_UPPER.equals(row)) {
            return currentOffer.topRowCardsToTake - this.localUpperChoicesUsed > 0;
        }
        if (ROW_LOWER.equals(row)) {
            return currentOffer.bottomRowCardsToTake - this.localLowerChoicesUsed > 0;
        }
        return false;
    }

    /** Returns whether a specific offer space can receive the current player's totem. */
    boolean canMoveTotemTo(OfferSpacePayload offer) {
        return offer != null
                && offer.occupiedByNickname == null
                && this.isCurrentUsersTurn()
                && this.isRoundPhase(PHASE_PLACING_TOTEMS);
    }

    /** Resets local card-pick counters when the preLobby advances to a new draft turn. */
    void updateLocalDraftTurn(GamePayload payload) {
        String newKey = draftTurnKey(payload);
        if (!Objects.equals(this.localDraftTurnKey, newKey)) {
            this.localDraftTurnKey = newKey;
            this.localUpperChoicesUsed = 0;
            this.localLowerChoicesUsed = 0;
        }
    }

    /** Clears the optimistic pending-row marker. */
    void clearPendingDraftRow() {
        this.pendingDraftRow = null;
    }

    /** Undoes the last optimistic card-choice counter after a preLobby error. */
    void rollbackPendingDraftChoice() {
        if (this.pendingDraftRow == null) {
            return;
        }
        if (ROW_UPPER.equals(this.pendingDraftRow) && this.localUpperChoicesUsed > 0) {
            this.localUpperChoicesUsed--;
        } else if (ROW_LOWER.equals(this.pendingDraftRow) && this.localLowerChoicesUsed > 0) {
            this.localLowerChoicesUsed--;
        }
        this.pendingDraftRow = null;
        GamePayload payload = this.gameSupplier.get();
        if (payload != null) {
            this.boardRefresher.accept(payload.board);
        }
    }

    /** Clears match-specific state after the final result screen. */
    void resetAfterMatch() {
        this.localDraftTurnKey = null;
        this.localUpperChoicesUsed = 0;
        this.localLowerChoicesUsed = 0;
        this.pendingDraftRow = null;
    }

    /** Updates the short action hint shown near the turn information. */
    void updateActionHint() {
        GamePayload payload = this.gameSupplier.get();
        if (payload == null || payload.turnOrder == null) {
            this.selectedCardLabel.setText("Waiting for match data.");
            return;
        }
        if (!this.isCurrentUsersTurn()) {
            this.selectedCardLabel.setText(this.currentTurnPlayerName() + "'s turn: your actions are paused.");
            return;
        }
        if (this.isRoundPhase(PHASE_PLACING_TOTEMS)) {
            this.selectedCardLabel.setText("Your turn: click a free offer-path space.");
            return;
        }
        if (this.isRoundPhase(PHASE_PLAYER_DRAWS)) {
            OfferSpacePayload currentOffer = this.currentPlayersOfferSpace();
            int upper = currentOffer == null ? 0 : Math.max(0, currentOffer.topRowCardsToTake - this.localUpperChoicesUsed);
            int lower = currentOffer == null ? 0 : Math.max(0, currentOffer.bottomRowCardsToTake - this.localLowerChoicesUsed);
            this.selectedCardLabel.setText("Your turn: choose cards from the market. Upper: "
                    + upper + " | lower: " + lower + ".");
            return;
        }
        if (this.isRoundPhase(PHASE_EXTRA_DRAWS)) {
            this.selectedCardLabel.setText("Extra draw: click an available card.");
            return;
        }
        this.selectedCardLabel.setText("Phase " + payload.turnOrder.currentRoundPhase + " in progress.");
    }

    /** Sets the selected-card label, used by GUIView for error feedback. */
    void setStatusMessage(String message) {
        this.selectedCardLabel.setText(message);
    }

    /** Builds the short turn description shown in the game header. */
    static String renderTurn(TurnOrderPayload order, TurnStatePayload state) {
        String phase = state == null ? "setup" : state.phase;
        if (order == null) {
            return "Phase " + phase + " | waiting for turn";
        }
        return "Round " + order.roundNumber + " | " + order.currentRoundPhase
                + " | " + order.currentPlayerNickname + "'s turn";
    }

    // ---------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------

    /** Records a local card choice before the preLobby confirmation arrives. */
    private void registerLocalCardChoice(String row) {
        if (ROW_UPPER.equals(row)) {
            this.localUpperChoicesUsed++;
        } else if (ROW_LOWER.equals(row)) {
            this.localLowerChoicesUsed++;
        }
    }

    /** Builds a stable key that identifies the current draft turn. */
    private static String draftTurnKey(GamePayload payload) {
        if (payload == null || payload.turnOrder == null) {
            return null;
        }
        return payload.id + "|"
                + payload.turnOrder.roundNumber + "|"
                + payload.turnOrder.currentRoundPhase + "|"
                + payload.turnOrder.currentPlayerNickname;
    }

    /** Finds the offer space currently occupied by the active player. */
    private OfferSpacePayload currentPlayersOfferSpace() {
        GamePayload payload = this.gameSupplier.get();
        if (payload == null
                || payload.board == null
                || payload.board.offerSpaces == null
                || payload.turnOrder == null) {
            return null;
        }
        String currentPlayer = payload.turnOrder.currentPlayerNickname;
        if (currentPlayer == null) {
            return null;
        }
        return payload.board.offerSpaces.stream()
                .filter(offer -> currentPlayer.equals(offer.occupiedByNickname))
                .findFirst()
                .orElse(null);
    }

    private boolean isCurrentUsersTurn() {
        GamePayload payload = this.gameSupplier.get();
        return payload != null
                && payload.turnOrder != null
                && this.nicknameSupplier.get().equals(payload.turnOrder.currentPlayerNickname);
    }

    private boolean isRoundPhase(String phase) {
        GamePayload payload = this.gameSupplier.get();
        return payload != null
                && payload.turnOrder != null
                && phase.equals(payload.turnOrder.currentRoundPhase);
    }

    private String currentTurnPlayerName() {
        GamePayload payload = this.gameSupplier.get();
        if (payload == null
                || payload.turnOrder == null
                || payload.turnOrder.currentPlayerNickname == null) {
            return "another player";
        }
        return payload.turnOrder.currentPlayerNickname;
    }
}
