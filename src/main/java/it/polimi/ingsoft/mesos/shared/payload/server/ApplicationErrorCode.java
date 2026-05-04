package it.polimi.ingsoft.mesos.shared.payload.server;

/**
 * Stable application error codes shared by all server transports.
 */
public enum ApplicationErrorCode {
    AUTH_ERROR,
    INVALID_MESSAGE,
    NOT_IN_LOBBY,
    LOBBY_FULL,
    NOT_PLAYERS_TURN,
    CARD_NOT_DRAWABLE,
    CONNECTION_CLOSED,
    INTERNAL_ERROR
}
