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

    public TurnPhase getTurnPhase() {
        return this.turnPhase;
    }

    public String getExpectedPlayer() {
        return this.expectedPlayer;
    }

    public GameStatus getGameStatus() {
        return this.gameStatus;
    }

    public Era getCurrentEra() {
        return this.currentEra;
    }

    public int getRoundNumber() {
        return this.roundNumber;
    }

    public boolean isTurnEnded() {
        return this.turnEnded;
    }

    public boolean isRoundEnded() {
        return this.roundEnded;
    }

    public boolean isGameEnded() {
        return this.gameEnded;
    }

    public List<Card> getUpperRowCards() {
        return this.upperRowCards;
    }

    public List<Card> getLowerRowCards() {
        return this.lowerRowCards;
    }

    public List<Card> getDrawableUpperCards() {
        return this.drawableUpperCards;
    }

    public List<Card> getDrawableLowerCards() {
        return this.drawableLowerCards;
    }
}
