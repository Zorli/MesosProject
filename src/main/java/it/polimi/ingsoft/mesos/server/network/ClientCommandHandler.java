package it.polimi.ingsoft.mesos.server.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.polimi.ingsoft.mesos.shared.messages.ClientMessageType;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.*;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.connection.*;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game.*;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.lobby.*;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby.*;
import it.polimi.ingsoft.mesos.server.virtualview.VirtualViewTCP;

import java.io.PrintStream;
import java.util.Objects;

public class ClientCommandHandler {
    private static final Gson GSON = new GsonBuilder().create();

    private final PrintStream out;
    private final VirtualViewTCP virtualView;

    public ClientCommandHandler(PrintStream out, VirtualViewTCP virtualView) {
        this.out = Objects.requireNonNull(out, "out cannot be null");
        this.virtualView = Objects.requireNonNull(virtualView, "virtualView cannot be null");
    }

    //TODO : Migliorare eccezioni lanciate qua per essere più informative
    public void handle(String rawMessage) {
        JsonObject root = GSON.fromJson(rawMessage, JsonObject.class);
        if (root == null) {
            throw new IllegalArgumentException("Malformed client message");
        }

        JsonElement typeElement = root.get("type");
        if (typeElement == null || typeElement.isJsonNull()) {
            throw new IllegalArgumentException("Missing client message type");
        }

        ClientMessageType type;
        try {
            type = ClientMessageType.valueOf(typeElement.getAsString());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unknown client message type", exception);
        }

        JsonElement payloadElement = root.get("payload");
        this.out.println("[TCP] Received " + type + " from " + this.virtualView.describeSession());

        // =========================================================
        // SMISTAMENTO E DESERIALIZZAZIONE
        // =========================================================

        // Se è un PONG, lo gestiamo istantaneamente bypassando la logica di gioco
        if (type == ClientMessageType.PONG) {
            this.virtualView.pong(new PongPayload());
            return; // Fine dell'esecuzione
        }

        // Per tutti gli altri messaggi, deserializziamo nella classe corretta
        // e imbustiamo il payload come ClientPayload generico
        ClientPayload payload = deserializePayload(type, payloadElement);

        if (payload == null) {
            throw new IllegalArgumentException("Malformed payload for type " + type);
        }

        // BAM! Unica chiamata alla VirtualView. Niente attesa, niente return.
        this.virtualView.sendMessage(payload);
    }

    // =========================================================
    // METODO DI SUPPORTO PER LA DESERIALIZZAZIONE
    // =========================================================
    private ClientPayload deserializePayload(ClientMessageType type, JsonElement payloadElement) {
        try {
            return switch (type) {
                case AUTHENTICATE -> GSON.fromJson(payloadElement, AuthenticationPayload.class);
                case LIST_LOBBIES -> GSON.fromJson(payloadElement, ListLobbiesPayload.class);
                case CREATE_LOBBY -> GSON.fromJson(payloadElement, CreateLobbyPayload.class);
                case JOIN_LOBBY   -> GSON.fromJson(payloadElement, JoinLobbyPayload.class);
                case EXIT_LOBBY   -> GSON.fromJson(payloadElement, ExitLobbyPayload.class);
                case START_LOBBY  -> GSON.fromJson(payloadElement, StartLobbyPayload.class);
                case MOVE_TOTEM   -> GSON.fromJson(payloadElement, MoveTotemPayload.class);
                case CHOOSE_CARD  -> GSON.fromJson(payloadElement, ChooseCardPayload.class);
                case EXTRA_DRAW   -> GSON.fromJson(payloadElement, ExtraDrawPayload.class);
                default -> throw new UnsupportedOperationException("Unsupported client message type: " + type);
            };
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed payload for " + type, exception);
        }
    }
}
