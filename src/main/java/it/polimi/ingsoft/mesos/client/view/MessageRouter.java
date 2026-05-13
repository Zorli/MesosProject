package it.polimi.ingsoft.mesos.client.view;


import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;

import java.util.Objects;

import static it.polimi.ingsoft.mesos.shared.messages.ServerMessageType.*;

//Questa classe dovrebbe essere chiamata con metodo univoco dal virtualserver indipendentemente dalla tecnologia di rete
//Ed in base al payload ricevuto che gli viene passato in mano dalla rete in entrata(virtualserver)
//Decide quale metodo chiamare della clientview per renderizzare quel determinato tipo di server messagge
//sa come, dato un payload, chiedere al generico UI di plottarlo
public class MessageRouter {
    private final ClientView clientView;

    public MessageRouter(ClientView clientView) {
        this.clientView = Objects.requireNonNull(clientView, "clientView cannot be null");
    }

    public void routeMessage(ServerMessage message) {
        Objects.requireNonNull(message, "message cannot be null");

        switch (message.type) {
            case ERROR -> clientView.showError((ErrorPayload) message.payload);
            case LOBBY_UPDATE -> clientView.showLobbyUpdate((LobbyUpdatePayload) message.payload);
            case LOBBY_LIST -> clientView.showLobbyList((LobbyListPayload) message.payload);
            case GAME_UPDATE -> {
                GamePayload payload = (GamePayload) message.payload;
                if ("FINISHED".equals(payload.status)) {
                    clientView.showFinalGameResult(payload);
                } else {
                    clientView.showGameUpdate(payload);
                }
            }
            case PING -> throw new IllegalArgumentException("PING must be handled by the virtual server");
        }
    }


}
