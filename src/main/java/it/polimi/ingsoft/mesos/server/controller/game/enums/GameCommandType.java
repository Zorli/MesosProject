package it.polimi.ingsoft.mesos.server.controller.game.enums;

import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.ChooseCardPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.ExtraDrawPayload;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.MoveTotemPayload;

public enum GameCommandType {
    CHOOSE_CARD,
    EXTRA_DRAW,
    MOVE_TOTEM;

    public static GameCommandType fromPayload(ClientPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        return switch (payload) {
            case ChooseCardPayload ignored -> CHOOSE_CARD;
            case ExtraDrawPayload ignored -> EXTRA_DRAW;
            case MoveTotemPayload ignored -> MOVE_TOTEM;
            default -> throw new IllegalArgumentException("Unexpected game payload: " + payload);
        };
    }
}
