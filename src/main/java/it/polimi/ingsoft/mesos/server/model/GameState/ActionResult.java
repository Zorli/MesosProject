package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.List;

/**
 * Immutable snapshot returned after a game action so the orchestrator can
 * observe the relevant match state
 *
 * */
public class ActionResult {
    private final TurnPhase turnPhase;
    private final String expectedPlayer;
    private final GameStatus gameStatus;
    private final Era currentEra;
    private final int roundNumber;
    private final boolean turnEnded;
    private final boolean roundEnded;
    private final boolean gameEnded;
    private final List<Card> upperRowCards;
    private final List<Card> lowerRowCards;
    private final List<Card> drawableUpperCards;
    private final List<Card> drawableLowerCards;

    /**
     * Creates an immutable snapshot that summarizes the relevant match state after one action.
     *
     * @param turnPhase current phase of the active player's turn
     * @param expectedPlayer player expected to act next
     * @param gameStatus lifecycle status of the game
     * @param currentEra era currently being played
     * @param roundNumber current round number
     * @param turnEnded whether the triggering action ended the turn
     * @param roundEnded whether the triggering action ended the round
     * @param gameEnded whether the triggering action ended the game
     * @param upperRowCards cards currently visible in the upper row
     * @param lowerRowCards cards currently visible in the lower row
     * @param drawableUpperCards upper-row cards drawable by the expected player
     * @param drawableLowerCards lower-row cards drawable by the expected player
     */
    public ActionResult(TurnPhase turnPhase, Player expectedPlayer, GameStatus gameStatus, Era currentEra,
                        int roundNumber, boolean turnEnded, boolean roundEnded, boolean gameEnded,
                        List<Card> upperRowCards, List<Card> lowerRowCards,
                        List<Card> drawableUpperCards, List<Card> drawableLowerCards) {
        if (turnPhase == null) {
            throw new IllegalArgumentException("Turn phase cannot be null");
        }
        if (gameStatus == null) {
            throw new IllegalArgumentException("Game status cannot be null");
        }
        if (currentEra == null) {
            throw new IllegalArgumentException("Current era cannot be null");
        }
        if (roundNumber < 0) {
            throw new IllegalArgumentException("Round number cannot be negative");
        }

        this.turnPhase = turnPhase;
        this.expectedPlayer = expectedPlayer == null ? null : expectedPlayer.getNickname();
        this.gameStatus = gameStatus;
        this.currentEra = currentEra;
        this.roundNumber = roundNumber;
        this.turnEnded = turnEnded;
        this.roundEnded = roundEnded;
        this.gameEnded = gameEnded;
        this.upperRowCards = upperRowCards == null ? List.of() : List.copyOf(upperRowCards);
        this.lowerRowCards = lowerRowCards == null ? List.of() : List.copyOf(lowerRowCards);
        this.drawableUpperCards = drawableUpperCards == null ? List.of() : List.copyOf(drawableUpperCards);
        this.drawableLowerCards = drawableLowerCards == null ? List.of() : List.copyOf(drawableLowerCards);
    }

    /**
     * Returns the phase of the turn after the action was applied.
     *
     * @return resulting turn phase
     */
    public TurnPhase getTurnPhase() {
        return this.turnPhase;
    }

    /**
     * Returns the nickname of the player expected to act next.
     *
     * @return expected player's nickname, or {@code null} when no player is expected
     */
    public String getExpectedPlayer() {
        return this.expectedPlayer;
    }

    /**
     * Returns the lifecycle status of the game after the action.
     *
     * @return resulting game status
     */
    public GameStatus getGameStatus() {
        return this.gameStatus;
    }

    /**
     * Returns the era active after the action.
     *
     * @return resulting current era
     */
    public Era getCurrentEra() {
        return this.currentEra;
    }

    /**
     * Returns the round number active after the action.
     *
     * @return resulting round number
     */
    public int getRoundNumber() {
        return this.roundNumber;
    }

    /**
     * Returns whether the action completed the current turn.
     *
     * @return {@code true} when the turn ended
     */
    public boolean isTurnEnded() {
        return this.turnEnded;
    }

    /**
     * Returns whether the action completed the current round.
     *
     * @return {@code true} when the round ended
     */
    public boolean isRoundEnded() {
        return this.roundEnded;
    }

    /**
     * Returns whether the action completed the whole game.
     *
     * @return {@code true} when the game ended
     */
    public boolean isGameEnded() {
        return this.gameEnded;
    }

    /**
     * Returns the cards visible in the upper board row.
     *
     * @return immutable upper-row card list
     */
    public List<Card> getUpperRowCards() {
        return this.upperRowCards;
    }

    /**
     * Returns the cards visible in the lower board row.
     *
     * @return immutable lower-row card list
     */
    public List<Card> getLowerRowCards() {
        return this.lowerRowCards;
    }

    /**
     * Returns the upper-row cards the active player may currently draft.
     *
     * @return immutable list of drawable upper-row cards
     */
    public List<Card> getDrawableUpperCards() {
        return this.drawableUpperCards;
    }

    /**
     * Returns the lower-row cards the active player may currently draft.
     *
     * @return immutable list of drawable lower-row cards
     */
    public List<Card> getDrawableLowerCards() {
        return this.drawableLowerCards;
    }
}
