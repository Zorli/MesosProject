package it.polimi.ingsoft.mesos.shared.messages;

public enum ClientMessageType {
    AUTHENTICATE,
    CREATE_LOBBY,
    JOIN_LOBBY,
    EXIT_LOBBY,
    START_LOBBY,
    SETUP_MATCH,
    MOVE_TOTEM,
    CHOOSE_CARD,
    EXTRA_DRAW,
    RESOLVE_EFFECTS,
    RESET_BOARD,
    FINAL_SCORE
}
