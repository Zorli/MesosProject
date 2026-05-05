package it.polimi.ingsoft.mesos.server.controller.GameOrchestrator;

import it.polimi.ingsoft.mesos.shared.payload.client.ChooseCardPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ClientPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExtraDrawPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.MoveTotemPayload;

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
