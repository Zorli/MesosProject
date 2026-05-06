package it.polimi.ingsoft.mesos.shared.messages;

public enum ClientMessageType {
    AUTHENTICATE,
    LIST_LOBBIES,
    LIST_GLOBAL_RANKING,
    LIST_MATCH_HISTORY,
    CREATE_LOBBY,
    JOIN_LOBBY,
    EXIT_LOBBY,
    START_LOBBY,
    MOVE_TOTEM,
    CHOOSE_CARD,
    EXTRA_DRAW,
    PONG
}
