package it.polimi.ingsoft.mesos.client.view;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.PreMatchUpdatePayload;

/**
 * Network-facing contract used by the client message router to deliver server updates.
 *
 * <p>Concrete UIs may implement this directly, as the TUI does, or through an adapter that forwards
 * payloads to a framework-specific controller, as the JavaFX GUI does.
 */
public interface ClientView {
    void showError(ErrorPayload payload);

    void showPreMatchUpdate(PreMatchUpdatePayload payload);

    void showLobbyList(LobbyListPayload payload);

    void showGameUpdate(GamePayload payload);
}
