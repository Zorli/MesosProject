package it.polimi.ingsoft.mesos.shared.messages;

public enum ClientMessageType {
    AUTHENTICATE,
    LIST_LOBBIES,
    CREATE_LOBBY,
    JOIN_LOBBY,
    EXIT_LOBBY,
    START_LOBBY,
    MOVE_TOTEM,
    CHOOSE_CARD,
    EXTRA_DRAW
}
