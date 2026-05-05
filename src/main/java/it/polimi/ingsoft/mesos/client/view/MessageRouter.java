package it.polimi.ingsoft.mesos.client.view;


import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import static it.polimi.ingsoft.mesos.shared.messages.ServerMessageType.*;

//Questa classe dovrebbe essere chiamata con metodo univoco dal virtualserver indipendentemente dalla tecnologia di rete
//Ed in base al payload ricevuto che gli viene passato in mano dalla rete in entrata(virtualserver)
//Decide quale metodo chiamare della clientview per renderizzare quel determinato tipo di server messagge
public class MessageRouter {
    ClientView clientView;
    public MessageRouter(ClientView clientView) {
        this.clientView = clientView;
    }

    public void routeMessage(ServerMessage message) {
        switch (message.type) {
            case ERROR -> clientView.showError((ErrorPayload) message.payload);
            case PRE_MATCH_UPDATE -> clientView.showPreMatchUpdate((PreMatchUpdatePayload) message.payload);
            case LOBBY_LIST -> clientView.showLobbyList((LobbyListPayload) message.payload);
            case GAME_UPDATE -> clientView.showGameUpdate((GamePayload) message.payload);
        }
    }


}
