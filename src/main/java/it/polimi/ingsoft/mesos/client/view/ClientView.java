package it.polimi.ingsoft.mesos.client.view;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.PreMatchUpdatePayload;
//This interface should be used to define the contract of what should be displayed, today it's only text and it's implemented
// by the TUI controller, sooner it will be implemneted by the GUicontroller and we should handle the graphic based on
//the payload
public interface ClientView {
    void showError(ErrorPayload payload);

    void showPreMatchUpdate(PreMatchUpdatePayload payload);

    void showLobbyList(LobbyListPayload payload);

    void showGameUpdate(GamePayload payload);
}
