package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.user.User;
import it.polimi.ingsoft.mesos.server.network.AuthenticationException;
import it.polimi.ingsoft.mesos.server.network.ConnectionContext;
import it.polimi.ingsoft.mesos.shared.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiServerService;
import it.polimi.ingsoft.mesos.shared.payload.client.*;
import it.polimi.ingsoft.mesos.shared.payload.server.*;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
/**
 * Dedicated RMI remote session for a single authenticated client.
 * <p>
 * Instances of this class are dynamically created by the {@link RmiSessionFactory}
 * upon successful login. This class acts as a private proxy for the client on the server,
 * maintaining their specific {@link ConnectionContext} and holding the remote reference
 * to their personal callback receiver ({@link RmiClientService}).
 * <p>
 * It is responsible for intercepting inbound gameplay commands from the client,
 * delegating the domain logic to the central {@link ServerController}, and dispatching
 * outbound server updates back to the client via asynchronous RMI callbacks.
 */
public class VirtualViewRMI extends UnicastRemoteObject implements RmiServerService {
    private final ServerController serverController;
    private final ConnectionContext connectionContext;
    private final RmiClientService clientCallback; // Per mandare gli update al client
    private GameOrchestrator gameOrchestrator;

    public VirtualViewRMI(ServerController serverController, RmiClientService clientCallback) throws RemoteException {
        super();
        this.serverController = serverController;
        this.clientCallback = clientCallback;
        this.connectionContext = new ConnectionContext(this.serverController);
    }
    public ConnectionContext getConnectionContext() {
        return this.connectionContext;
    }
    
    private void requireAuthenticated() {
        if (this.connectionContext.isInAuthenticationPhase()) {
            throw new AuthenticationException("Authentication required before this operation");
        }
    }

    private ErrorPayload mapExceptionToErrorPayload(Exception exception) {
        if (exception instanceof AuthenticationException) {
            return new ErrorPayload("AUTHENTICATION_FAILED", exception.getMessage());
        }
        if (exception instanceof IllegalArgumentException) {
            return new ErrorPayload("INVALID_MESSAGE", exception.getMessage());
        }
        if (exception instanceof UnsupportedOperationException) {
            return new ErrorPayload("INVALID_MESSAGE", exception.getMessage());
        }
        if (exception instanceof IllegalStateException) {
            return new ErrorPayload("INVALID_STATE", exception.getMessage());
        }
        return new ErrorPayload("INTERNAL_ERROR", "Unexpected server error");
    }
    @Override
    public void joinLobby(JoinLobbyPayload payload) throws RemoteException {
        try {
            this.serverController.joinLobby(payload.lobbyId, this.connectionContext.getUser());
            this.connectionContext.setCurrentLobbyId(payload.lobbyId);
            Lobby lobby = this.serverController.getLobby(payload.lobbyId).orElse(null);
            PreMatchUpdatePayload update = this.buildPreMatchUpdatePayload(
                    PreMatchEventType.LOBBY_JOINED,
                    this.connectionContext.getNickname(),
                    "Lobby joined successfully",
                    lobby
            );

            this.clientCallback.handlePreMatchUpdate(update);
        } catch (Exception e) {
            this.clientCallback.handleError(mapExceptionToErrorPayload(e));
        }
    }
    @Override
    public void createLobby(CreateLobbyPayload payload) throws RemoteException{
        try {
            Lobby lobby = this.serverController.createLobby(this.connectionContext.getUser(), payload.chosenCapacity);
            this.connectionContext.setCurrentLobbyId(lobby.getId());
            PreMatchUpdatePayload update = this.buildPreMatchUpdatePayload(
                    PreMatchEventType.LOBBY_CREATED,
                    this.connectionContext.getNickname(),
                    "Lobby created successfully",
                    lobby
            );

            this.clientCallback.handlePreMatchUpdate(update);
        } catch (Exception e) {
            this.clientCallback.handleError(mapExceptionToErrorPayload(e));
        }
    }
    @Override
    public void exitLobby(ExitLobbyPayload payload) throws RemoteException {
        try {
            this.serverController.exitLobby(payload.lobbyId, this.connectionContext.getUser());
            Lobby lobby = this.serverController.getLobby(payload.lobbyId).orElse(null);
            this.connectionContext.setCurrentLobbyId(null);

            PreMatchUpdatePayload update = this.buildPreMatchUpdatePayload(
                    PreMatchEventType.LOBBY_LEFT,
                    this.connectionContext.getNickname(),
                    lobby == null ? "Lobby closed" : "Lobby left successfully",
                    lobby,
                    payload.lobbyId
            );

            this.clientCallback.handlePreMatchUpdate(update);
        } catch (Exception e) {
            this.clientCallback.handleError(mapExceptionToErrorPayload(e));
        }
    }
    @Override
    public void listLobbies(ListLobbiesPayload payload) throws RemoteException {
        try{
            List<LobbySummaryPayload> lobbies = this.serverController.listOpenLobbies().stream()
                    .map(this::toLobbySummaryPayload)
                    .toList();
            this.clientCallback.handleLobbyList(new LobbyListPayload(lobbies));
        } catch (Exception e) {
            this.clientCallback.handleError(mapExceptionToErrorPayload(e));
        }
    }
    @Override
    public void startLobby(StartLobbyPayload payload) throws RemoteException {
        Integer lobbyId = this.connectionContext.getCurrentLobbyId();
        this.gameOrchestrator = this.serverController.createGameFromLobby(lobbyId);
        this.connectionContext.enterMatch(this.gameOrchestrator);
        Lobby lobby = this.serverController.getLobby(lobbyId).orElse(null);
        PreMatchUpdatePayload update = this.buildPreMatchUpdatePayload(
                PreMatchEventType.LOBBY_STARTED,
                this.connectionContext.getNickname(),
                "Lobby started successfully",
                lobby,
                lobbyId
        );
        this.clientCallback.handlePreMatchUpdate(update);
    }
    @Override
    public void setupMatch(SetupMatchPayload payload) throws RemoteException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void moveTotem(MoveTotemPayload payload)  throws RemoteException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void chooseCard(ChooseCardPayload payload)  throws RemoteException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void extraDraw(ExtraDrawPayload payload)  throws RemoteException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void resolveEffects(ResolveEffectsPayload payload)  throws RemoteException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void resetBoard(ResetBoardPayload payload)  throws RemoteException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void finalScore(FinalScorePayload payload)  throws RemoteException {
        throw new UnsupportedOperationException("Not implemented yet");
    }
    @Override
    public void closeSession() throws RemoteException {
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException ignored) {}
    }
    //
    // METODI HELPER PER COSTRUZIONE DEI PAYLOAD//
    //
    private List<String> extractPlayers(Lobby lobby) {
        return lobby.getUsers().stream()
                .map(User::getNickname)
                .toList();
    }

    private PreMatchUpdatePayload buildPreMatchUpdatePayload(PreMatchEventType event, String nickname, String message, Lobby lobby) {
        return this.buildPreMatchUpdatePayload(event, nickname, message, lobby, null);
    }

    private PreMatchUpdatePayload buildPreMatchUpdatePayload(
            PreMatchEventType event,
            String nickname,
            String message,
            Lobby lobby,
            Integer fallbackLobbyId
    ) {
        return new PreMatchUpdatePayload(
                event,
                nickname,
                message,
                lobby != null ? String.valueOf(lobby.getId()) : fallbackLobbyId == null ? null : String.valueOf(fallbackLobbyId),
                lobby != null ? this.extractPlayers(lobby) : List.of(),
                lobby != null ? lobby.getHostNickname() : null,
                lobby != null ? lobby.getChosenCapacity() : null
        );
    }
    private LobbySummaryPayload toLobbySummaryPayload(Lobby lobby) {
        return new LobbySummaryPayload(
                lobby.getId(),
                lobby.getHostNickname(),
                lobby.getUsers().size(),
                lobby.getChosenCapacity(),
                lobby.getStatus().name()
        );
    }
}
