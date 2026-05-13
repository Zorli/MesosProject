package it.polimi.ingsoft.mesos.client.view;

import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;

import java.util.Objects;

/**
 * Routes server messages to the correct client view method.
 *
 * <p>The virtual server receives messages from the real server. The transport
 * implementation can be TCP or RMI, but after a {@link ServerMessage} has been
 * received the rest of the client should not care about the transport anymore.
 * This class is the small adapter between the network-facing side of the client
 * and the user-facing side of the client.
 *
 * <p>The router looks at the message type, casts the payload to the matching
 * payload class, and calls the corresponding method on {@link ClientView}.
 */
public class MessageRouter {
    private final ClientView clientView;

    /**
     * Creates a router connected to one client view.
     *
     * @param clientView view that will render routed payloads
     * @throws NullPointerException if {@code clientView} is {@code null}
     */
    public MessageRouter(ClientView clientView) {
        this.clientView = Objects.requireNonNull(clientView, "clientView cannot be null");
    }

    /**
     * Sends one server message to the view method that matches its type.
     *
     * <p>{@link ServerMessageType#PING} is rejected because ping messages are
     * part of the connection keep-alive protocol. They are handled directly by
     * the virtual server and are not user-visible payloads.
     *
     * @param message message received from the server
     * @throws NullPointerException if {@code message} is {@code null}
     * @throws IllegalArgumentException if the message is a ping
     */
    public void routeMessage(ServerMessage message) {
        Objects.requireNonNull(message, "message cannot be null");

        switch (message.type) {
            case ERROR:
                this.clientView.showError((ErrorPayload) message.payload);
                break;
            case LOBBY_UPDATE:
                this.clientView.showLobbyUpdate((LobbyUpdatePayload) message.payload);
                break;
            case LOBBY_LIST:
                this.clientView.showLobbyList((LobbyListPayload) message.payload);
                break;
            case GAME_UPDATE:
                this.routeGameUpdate((GamePayload) message.payload);
                break;
            case PING:
                throw new IllegalArgumentException("PING must be handled by the virtual server");
        }
    }

    private void routeGameUpdate(GamePayload payload) {
        if ("FINISHED".equals(payload.status)) {
            this.clientView.showFinalGameResult(payload);
        } else {
            this.clientView.showGameUpdate(payload);
        }
    }
}
