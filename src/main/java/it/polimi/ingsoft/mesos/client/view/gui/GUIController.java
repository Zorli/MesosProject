package it.polimi.ingsoft.mesos.client.view.gui;

import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxy;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.FinalMatchRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GlobalRankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.MatchHistoryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

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
    public void showPreMatchUpdate(PreMatchUpdatePayload payload) {
    }

    @Override
    public void showLobbyList(LobbyListPayload payload) {
    }

    @Override
    public void showGameUpdate(GamePayload payload) {
    }

    @Override
    public void showGlobalRanking(GlobalRankingPayload payload) {
    }

    @Override
    public void showFinalMatchRanking(FinalMatchRankingPayload payload) {
    }

    @Override
    public void showMatchHistory(MatchHistoryPayload payload) {
    }

}
