package it.polimi.ingsoft.mesos.client.view.gui;

import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxy;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;

import java.util.Objects;

//here we should insert the logic of the UI, remember that this is running on the Javafx thread
//For every network necessity it must use the virtualserver
public class GUIController implements ClientView {
    private ServerProxy serverProxy;

    public void attachServerProxy(ServerProxy serverProxy) {
        this.serverProxy = Objects.requireNonNull(serverProxy, "serverProxy cannot be null");
    }

    public void start() {
        System.out.println("GUI controller is not implemented yet.");
    }

    @Override
    public void showError(ErrorPayload payload) {
    }

    @Override
    public void showLobbyUpdate(LobbyUpdatePayload payload) {
    }

    @Override
    public void showLobbyList(LobbyListPayload payload) {
    }

    @Override
    public void showGameUpdate(GamePayload payload) {
    }

}
