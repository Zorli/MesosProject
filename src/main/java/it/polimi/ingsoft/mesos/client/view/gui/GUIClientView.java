package it.polimi.ingsoft.mesos.client.view.gui;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.PreMatchUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;

import java.util.Objects;

/**
 * Adapter between the network-facing {@link ClientView} contract and the JavaFX controller.
 */
public class GUIClientView implements ClientView {
    private final GUIController controller;

    public GUIClientView(GUIController controller) {
        this.controller = Objects.requireNonNull(controller, "controller cannot be null");
    }

    public GUIController getController() {
        return this.controller;
    }

    @Override
    public void showError(ErrorPayload payload) {
        this.controller.displayError(payload);
    }

    @Override
    public void showPreMatchUpdate(PreMatchUpdatePayload payload) {
        this.controller.displayPreMatchUpdate(payload);
    }

    @Override
    public void showLobbyList(LobbyListPayload payload) {
        this.controller.displayLobbyList(payload);
    }

    @Override
    public void showGameUpdate(GamePayload payload) {
        this.controller.displayGameUpdate(payload);
    }
}
