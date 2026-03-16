package it.polimi.ingsoft.mesos.server.model.enums;

/**
 * Describes the lifecycle status of a running game.
 */
public enum GameStatus {
    /** The game has been created but setup is not completed yet. */
    SETUP,
    /** The match is actively being played. */
    IN_PROGRESS,
    /** The match reached its end and final scoring can be shown. */
    ENDED
}
