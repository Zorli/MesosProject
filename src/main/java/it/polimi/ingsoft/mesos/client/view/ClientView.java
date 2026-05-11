package it.polimi.ingsoft.mesos.client.view;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.PreMatchUpdatePayload;

/**
 * Network-facing contract used by the client message router to deliver server updates.
 *
 * <p>Concrete UI controllers implement this directly so the router can deliver typed payloads
 * without knowing whether the client is running in TUI or GUI mode.
 */
public interface ClientView {
    void showError(ErrorPayload payload);

    void showPreMatchUpdate(PreMatchUpdatePayload payload);

    void showLobbyList(LobbyListPayload payload);

    void showGameUpdate(GamePayload payload);

    default void showFinalGameResult(GamePayload payload) {
        showGameUpdate(payload);
    }
}
