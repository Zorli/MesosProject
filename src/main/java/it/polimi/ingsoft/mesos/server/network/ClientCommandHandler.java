package it.polimi.ingsoft.mesos.server.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.server.virtualview.VirtualView;

import java.io.PrintStream;
import java.util.Objects;


// !!!!! Vital !!!!!
// Command handlers call application-layer methods that may throw exceptions.
// When a called method can fail with a generic or domain-level exception, catch it here
// and rethrow a more precise protocol-facing exception. The VirtualView boundary then
// catches that exception and maps it to the correct ERROR message for the client.
//!!!!!!!!!

//Here we maintain only the de-serialization logic and the parsing logic, all the business logic is inside the virtualviewtcp
//that implements the common contract in virtualview. So here in the call we check the payload and the TCP semantics
//In the virtualview method we check for the applicability of that method(everythin non related to network)
public class ClientCommandHandler {
    private static final Gson GSON = new GsonBuilder().create();

    private final PrintStream out;
    private final VirtualView virtualView;

    public ClientCommandHandler(PrintStream out, VirtualView virtualView) {
        this.out = Objects.requireNonNull(out, "out cannot be null");
        this.virtualView = Objects.requireNonNull(virtualView, "virtualView cannot be null");
    }

    // Entry point
    public ServerMessage handle(String rawMessage) {
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

        JsonElement payload = root.get("payload");
        this.out.println("[TCP] Received " + type
                + " from " + this.virtualView.describeSession()
                + " payload=" + (payload == null ? "null" : payload));

        if (type != ClientMessageType.AUTHENTICATE && !this.virtualView.isAuthenticated()) {
            throw new AuthenticationException("Client is not authenticated");
        }

        // Command dispatch
        return switch (type) {
            case AUTHENTICATE -> this.handleAuthentication(payload);
            case LIST_LOBBIES -> this.handleListLobbies(payload);
            case CREATE_LOBBY -> this.handleCreateLobby(payload);
            case JOIN_LOBBY -> this.handleJoinLobby(payload);
            case EXIT_LOBBY -> this.handleExitLobby(payload);
            case START_LOBBY -> this.handleStartLobby(payload);
            case MOVE_TOTEM -> this.handleMoveTotem(payload);
            case CHOOSE_CARD -> this.handleChooseCard(payload);
            case EXTRA_DRAW -> this.handleExtraDraw(payload);
            case PONG -> this.handlePong(payload);
            default -> throw new UnsupportedOperationException("Unsupported client message type: " + type);
        };
    }

    // Command-specific payload handling
    private ServerMessage handleAuthentication(JsonElement payload) {
        AuthenticationPayload authenticationPayload;
        try {
            authenticationPayload = GSON.fromJson(payload, AuthenticationPayload.class);
            if (authenticationPayload == null) {
                throw new IllegalArgumentException("Malformed authentication payload");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed authentication payload", exception);
        }

        return this.virtualView.authenticate(authenticationPayload);
    }

    private ServerMessage handleCreateLobby(JsonElement payload) {
        CreateLobbyPayload createLobbyPayload;
        try {
            createLobbyPayload = GSON.fromJson(payload, CreateLobbyPayload.class);
            if (createLobbyPayload == null) {
                throw new IllegalArgumentException("Malformed create-lobby payload");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed create-lobby payload", exception);
        }

        return this.virtualView.createLobby(createLobbyPayload);
    }

    private ServerMessage handleListLobbies(JsonElement payload) {
        ListLobbiesPayload listLobbiesPayload;
        try {
            listLobbiesPayload = GSON.fromJson(payload, ListLobbiesPayload.class);
            if (listLobbiesPayload == null) {
                throw new IllegalArgumentException("Malformed list-lobbies payload");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed list-lobbies payload", exception);
        }

        return this.virtualView.listLobbies(listLobbiesPayload);
    }

    private ServerMessage handleJoinLobby(JsonElement payload) {
        JoinLobbyPayload joinLobbyPayload;
        try {
            joinLobbyPayload = GSON.fromJson(payload, JoinLobbyPayload.class);
            if (joinLobbyPayload == null) {
                throw new IllegalArgumentException("Malformed join-lobby payload");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed join-lobby payload", exception);
        }

        if (joinLobbyPayload.lobbyId <= 0) {
            throw new IllegalArgumentException("Join-lobby payload must contain a valid lobbyId");
        }

        return this.virtualView.joinLobby(joinLobbyPayload);
    }

    private ServerMessage handleExitLobby(JsonElement payload) {
        ExitLobbyPayload exitLobbyPayload;
        try {
            exitLobbyPayload = GSON.fromJson(payload, ExitLobbyPayload.class);
            if (exitLobbyPayload == null) {
                throw new IllegalArgumentException("Malformed exit-lobby payload");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed exit-lobby payload", exception);
        }

        return this.virtualView.exitLobby(exitLobbyPayload);
    }

    private ServerMessage handleStartLobby(JsonElement payload) {
        StartLobbyPayload startLobbyPayload;
        try {
            startLobbyPayload = GSON.fromJson(payload, StartLobbyPayload.class);
            if (startLobbyPayload == null) {
                throw new IllegalArgumentException("Malformed start-lobby payload");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed start-lobby payload", exception);
        }

        return this.virtualView.startLobby(startLobbyPayload);
    }

    private ServerMessage handleMoveTotem(JsonElement payload) {
        MoveTotemPayload moveTotemPayload;
        try {
            moveTotemPayload = GSON.fromJson(payload, MoveTotemPayload.class);
            if (moveTotemPayload == null) {
                throw new IllegalArgumentException("Malformed move-totem payload");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed move-totem payload", exception);
        }

        if (moveTotemPayload.tile == null || moveTotemPayload.tile.isBlank()) {
            throw new IllegalArgumentException("Move-totem payload must contain a tile");
        }

        return this.virtualView.moveTotem(moveTotemPayload);
    }

    private ServerMessage handleChooseCard(JsonElement payload) {
        ChooseCardPayload chooseCardPayload;
        try {
            chooseCardPayload = GSON.fromJson(payload, ChooseCardPayload.class);
            if (chooseCardPayload == null) {
                throw new IllegalArgumentException("Malformed choose-card payload");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed choose-card payload", exception);
        }

        return this.virtualView.chooseCard(chooseCardPayload);
    }

    private ServerMessage handleExtraDraw(JsonElement payload) {
        ExtraDrawPayload extraDrawPayload;
        try {
            extraDrawPayload = GSON.fromJson(payload, ExtraDrawPayload.class);
            if (extraDrawPayload == null) {
                throw new IllegalArgumentException("Malformed extra-draw payload");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed extra-draw payload", exception);
        }

        return this.virtualView.extraDraw(extraDrawPayload);
    }

    private ServerMessage handlePong(JsonElement payload) {
        PongPayload pongPayload;
        try {
            pongPayload = GSON.fromJson(payload, PongPayload.class);
            if (pongPayload == null) {
                throw new IllegalArgumentException("Malformed pong payload");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed pong payload", exception);
        }

        this.virtualView.recordPong();
        return null;
    }
}
