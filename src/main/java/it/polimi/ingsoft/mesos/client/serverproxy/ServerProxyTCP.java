package it.polimi.ingsoft.mesos.client.serverproxy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.virtualserver.VirtualServerTCP;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessage;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessageType;
import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ChooseCardPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.CreateLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExitLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExtraDrawPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.JoinLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ListLobbiesPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.MoveTotemPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.PongPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.StartLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Objects;


// TCP implementation of the client -> server channel.
// The UI controllers use only the ServerProxy contract, without knowing if the network is TCP or RMI.
// This class owns the TCP socket writer: every outbound client message, including PONG replies,
// must pass through here

public class ServerProxyTCP implements ServerProxy {
    private static final Gson GSON = new GsonBuilder().create();

    private final Socket socket;
    private final PrintWriter writer;
    private final ClientView clientView;
    private final VirtualServerTCP virtualServer;
    private final Thread virtualServerThread;

    public ServerProxyTCP(String host, int port, ClientView clientView) throws IOException {
        Objects.requireNonNull(host, "host cannot be null");
        Objects.requireNonNull(clientView, "clientView cannot be null");
        this.socket = new Socket(host, port);
        this.writer = new PrintWriter(this.socket.getOutputStream(), true);
        this.clientView = clientView;
        this.virtualServer = new VirtualServerTCP(this.socket, clientView, this);
        this.virtualServerThread = new Thread(this.virtualServer, "mesos-client-server-message-receiver");
        this.virtualServerThread.start();
    }

    ServerProxyTCP(Socket socket, ClientView clientView) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket cannot be null");
        this.clientView = Objects.requireNonNull(clientView, "clientView cannot be null");
        this.writer = new PrintWriter(this.socket.getOutputStream(), true);
        this.virtualServer = new VirtualServerTCP(this.socket, clientView, this);
        this.virtualServerThread = new Thread(this.virtualServer, "mesos-client-server-message-receiver");
        this.virtualServerThread.start();
    }

    void waitForVirtualServer() throws InterruptedException {
        this.virtualServerThread.join(1000);
    }

    @Override
    public boolean isConnected() {
        return this.socket.isConnected() && !this.socket.isClosed();
    }

    private void send(ClientMessage message) {
        Objects.requireNonNull(message, "message cannot be null");
        if (!this.isConnected()) {
            this.notifyLocalError("CONNECTION_CLOSED", "Client is not connected");
            return;
        }
        this.writer.println(GSON.toJson(message));
        if (this.writer.checkError()) {
            this.notifyLocalError("CONNECTION_CLOSED", "Cannot send message to server");
        }
    }

    private void notifyLocalError(String code, String message) {
        this.clientView.showError(new ErrorPayload(code, message));
    }

    public void sendPong() {
        this.send(new ClientMessage(ClientMessageType.PONG, new PongPayload()));
    }

    @Override
    public void authenticate(String nickname) {
        this.send(new ClientMessage(ClientMessageType.AUTHENTICATE, new AuthenticationPayload(nickname)));
    }

    @Override
    public void listLobbies() {
        this.send(new ClientMessage(ClientMessageType.LIST_LOBBIES, new ListLobbiesPayload()));
    }

    @Override
    public void createLobby(int chosenCapacity) {
        this.send(new ClientMessage(ClientMessageType.CREATE_LOBBY, new CreateLobbyPayload(chosenCapacity)));
    }

    @Override
    public void joinLobby(int lobbyId) {
        this.send(new ClientMessage(ClientMessageType.JOIN_LOBBY, new JoinLobbyPayload(lobbyId)));
    }

    @Override
    public void exitLobby() {
        this.send(new ClientMessage(ClientMessageType.EXIT_LOBBY, new ExitLobbyPayload()));
    }

    @Override
    public void startLobby() {
        this.send(new ClientMessage(ClientMessageType.START_LOBBY, new StartLobbyPayload()));
    }

    @Override
    public void moveTotem(String tile) {
        this.send(new ClientMessage(ClientMessageType.MOVE_TOTEM, new MoveTotemPayload(tile)));
    }

    @Override
    public void chooseCard(int cardId) {
        this.send(new ClientMessage(ClientMessageType.CHOOSE_CARD, new ChooseCardPayload(cardId)));
    }

    @Override
    public void extraDraw(int cardId) {
        this.send(new ClientMessage(ClientMessageType.EXTRA_DRAW, new ExtraDrawPayload(cardId)));
    }

    @Override
    public synchronized void close() throws IOException {
        this.virtualServer.requestCloseByClient();
        this.closeConnection();
    }

    public synchronized void closeConnection() throws IOException {
        if (!this.socket.isClosed()) {
            this.socket.close();
        }
        this.writer.close();
    }

}
