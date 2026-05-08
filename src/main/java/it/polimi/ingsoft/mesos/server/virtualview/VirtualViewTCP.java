package it.polimi.ingsoft.mesos.server.virtualview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.network.ClientCommandHandler;
import it.polimi.ingsoft.mesos.server.network.ClientConnection;
import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.network.MessageRouter;
import it.polimi.ingsoft.mesos.server.network.ProtocolErrorMapper;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.payload.client.*;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchEventType;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

//TCP implementation of the VirtualView contract.
//It acts as the network boundary on the server side: it reads raw client messages,
//delegates parsing/dispatch, and serializes the structured server response back on the socket.
public class VirtualViewTCP implements VirtualView, ClientConnection, Runnable, Listener{
    private static final Gson GSON = new GsonBuilder().create();

    private final Socket clientSocket;
    private final ServerController serverController;
    private final PrintStream out;
    private final PrintWriter writer;
    private final String sessionId;
    private final MessageRouter router;
    private final ClientCommandHandler commandHandler;
    private boolean cleanupDone;

    //Consutrctor
    public VirtualViewTCP(Socket clientSocket, ServerController serverController, MessageRouter router) {
        this(clientSocket, serverController, router, System.out);
    }

    public VirtualViewTCP(Socket clientSocket, ServerController serverController,MessageRouter router, PrintStream out) {
        this.clientSocket = Objects.requireNonNull(clientSocket, "clientSocket cannot be null");
        this.serverController = Objects.requireNonNull(serverController, "serverController cannot be null");
        this.router = router;
        this.out = Objects.requireNonNull(out, "out cannot be null");
        try {
            this.writer = new PrintWriter(
                    new OutputStreamWriter(this.clientSocket.getOutputStream(), StandardCharsets.UTF_8),
                    true
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot open TCP virtual view writer", exception);
        }
        this.sessionId = this.serverController.registerConnection(this);
        this.commandHandler = new ClientCommandHandler(this.out, this);
    }

    // Main Loop on the socket
    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(this.clientSocket.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String rawMessage;

            while ((rawMessage = reader.readLine()) != null) {
                try {
                    // Il commandHandler fa il parse del JSON e lancia il payload nel MessageRouter.
                    // Non ci sono più valori di ritorno da aspettare.
                    this.commandHandler.handle(rawMessage);

                } catch (RuntimeException exception) {
                    // Se il pacchetto è palesemente malformato, non disturba nemmeno
                    // i Controller e risponde subito con l'errore.
                    this.sendError(exception);
                }
            }
        } catch (IOException exception) {
            if (!this.cleanupDone) {
                this.out.println("[TCP] Connection closed/lost for " + this.describeSession() + ": " + exception.getMessage());
            }
        } finally {
            try {
                //pessimo
                this.close();
            } catch (IOException ignored) {
            }
        }
    }

    // I/O methods section---------------
    private void sendServerMessage(ServerMessage message) {//low level call
        Objects.requireNonNull(message, "message cannot be null");
        this.writer.println(GSON.toJson(message));
    }

    // Error handling method
    private void sendError(RuntimeException exception) {
        Objects.requireNonNull(exception, "exception cannot be null");
        ErrorPayload errorPayload = ProtocolErrorMapper.toErrorPayload(exception);
        this.sendServerMessage(new ServerMessage(ServerMessageType.ERROR, errorPayload));
    }


    //Utils
    public String describeSession() {
        String nickname = this.serverController.getSessionRegistry().nicknameOf(this.sessionId);
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        return "unauthenticated-client";
    }

    //Chaiamata interna per mandare messaggio al router, in tcp deve essere chiamata
    //dal deserializzatore
    @Override
    public void sendMessage(ClientPayload payload) {
        this.router.routeMessage(this.sessionId, payload);
    }

    @Override
    public void notifyUpdate(Object payload) {
        if (payload instanceof GamePayload gamePayload) {
            this.sendServerMessage(new ServerMessage(ServerMessageType.GAME_UPDATE, gamePayload));
            return;
        }
        if (payload instanceof ErrorPayload errorPayload) {
            this.sendServerMessage(new ServerMessage(ServerMessageType.ERROR, errorPayload));
            return;
        }
        if (payload instanceof LobbyListPayload lobbyListPayload) {
            this.sendServerMessage(new ServerMessage(ServerMessageType.LOBBY_LIST, lobbyListPayload));
            return;
        }
        if (payload instanceof PreMatchUpdatePayload preMatchUpdatePayload) {
            if (preMatchUpdatePayload.event == PreMatchEventType.LOBBY_STARTED){
            }
            this.sendServerMessage(new ServerMessage(ServerMessageType.PRE_MATCH_UPDATE, preMatchUpdatePayload));
        }
    }


    //Ping Pong protocol
    @Override
    public void sendPing() {
        this.sendServerMessage(new ServerMessage(ServerMessageType.PING, null));
    }

    @Override
    public void pong(PongPayload payload) {
        if (payload == null) {
            this.sendError(new IllegalArgumentException("Malformed payload"));
            return;
        }
        this.serverController.recordPong(this.sessionId);
    }


    //Metodi di chiusura pulita
    @Override
    public void closeSession() {
        try {
            this.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (this.cleanupDone) {
            return;
        }
        this.cleanupDone = true;
        this.serverController.disconnectSession(this.sessionId);
        if (!this.clientSocket.isClosed()) {
            this.clientSocket.close();
        }
    }


}
