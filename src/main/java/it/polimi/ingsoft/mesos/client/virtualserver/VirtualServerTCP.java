package it.polimi.ingsoft.mesos.client.virtualserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessage;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessageType;
import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Objects;

public class VirtualServerTCP implements VirtualServer {
    private static final Gson GSON = new GsonBuilder().create();

    private final Socket socket;
    private final PrintWriter writer;
    private final ClientView clientView;
    private final TcpServerMessageReceiver messageReceiver;
    private final Thread messageReceiverThread;

    public VirtualServerTCP(String host, int port, ClientView clientView) throws IOException {
        Objects.requireNonNull(host, "host cannot be null");
        Objects.requireNonNull(clientView, "clientView cannot be null");
        this.clientView = clientView;
        this.socket = new Socket(host, port);
        this.writer = new PrintWriter(this.socket.getOutputStream(), true);
        this.messageReceiver = new TcpServerMessageReceiver(this.socket, this);
        this.messageReceiverThread = new Thread(this.messageReceiver, "mesos-client-server-message-receiver");
        this.messageReceiverThread.start();
    }

    @Override
    public boolean isConnected() {
        return this.socket.isConnected() && !this.socket.isClosed();
    }

    @Override
    public void send(ClientMessage message) {
        Objects.requireNonNull(message, "message cannot be null");
        this.writer.println(GSON.toJson(message));
    }

    @Override
    public void sendRaw(String serializedMessage) {
        Objects.requireNonNull(serializedMessage, "serializedMessage cannot be null");
        this.writer.println(serializedMessage);
    }

    @Override
    public void authenticate(String nickname) {
        this.send(new ClientMessage(ClientMessageType.AUTHENTICATE, new AuthenticationPayload(nickname)));
    }

    @Override
    public void handleError(ErrorPayload payload) {
        this.clientView.showError(payload);
    }

    @Override
    public void handlePreMatchUpdate(PreMatchUpdatePayload payload) {
        this.clientView.showPreMatchUpdate(payload);
    }

    @Override
    public void handleGameUpdate(GamePayload payload) {
        this.clientView.showGameUpdate(payload);
    }

    @Override
    public void close() throws IOException {
        this.writer.close();
        this.socket.close();
    }
}
