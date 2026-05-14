package it.polimi.ingsoft.mesos.server.model.game.game.enums;

/**
 * High-level phase of a game round.
 *
 * <p>The round phase tells which kind of action is currently legal in the
 * match. Player commands such as {@code moveTotem}, {@code chooseCard}, and
 * {@code extraDrawn} are not phases. They are actions allowed only during a
 * specific phase.
 */
public enum RoundPhase {
    /**
     * Players move their totems from the starting tile to offer spaces.
     */
    PLACING_TOTEMS,

    /**
     * Players draw the cards allowed by the offer spaces they selected.
     */
    PLAYER_DRAWS,

    /**
     * Players with an end-round extra-card effect draw one additional card.
     */
    EXTRA_DRAWS,

    /**
     * The game resolves end-of-round event effects.
     */
    RESOLVING_EFFECTS,

    /**
     * The board is cleaned and refilled for the next round.
     */
    RESETTING_BOARD,

    /**
     * The game calculates final points after the last round.
     */
    FINAL_SCORING
}
