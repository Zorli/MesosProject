package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.controller.PreMatchPayloadMapper;
import it.polimi.ingsoft.mesos.shared.RmiClientService;
import it.polimi.ingsoft.mesos.shared.RmiConnectionService;
import it.polimi.ingsoft.mesos.shared.RmiServerService;
import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
/**
 * RMI entry point and session factory for incoming client connections.
 * <p>
 * This singleton object is bound to the server's RMI Registry and acts as the public
 * "Gatekeeper". It exposes the initial authentication endpoint for all connecting clients.
 * <p>
 * When a client successfully authenticates via this facade, the factory instantiates
 * and exports a dedicated, stateful {@link VirtualViewRMI} session exclusively for that user.
 * This "Session Factory" pattern ensures that unauthenticated clients cannot access
 * core game logic and that each connected client maintains a strictly isolated connection context.
 */
public class RmiSessionFactory extends UnicastRemoteObject implements RmiConnectionService {
    private final ServerController serverController;
    private final PreMatchPayloadMapper payloadMapper;

    public RmiSessionFactory(ServerController serverController) throws RemoteException {
        super();
        this.serverController = serverController;
        this.payloadMapper = new PreMatchPayloadMapper();
    }

    @Override
    public RmiServerService authenticate(AuthenticationPayload payload, RmiClientService clientCallback) throws RemoteException {
        if (payload == null || payload.nickname == null || payload.nickname.isBlank()) {
            System.err.println("[RMI] Tentativo di login con nickname vuoto o nullo.");
            clientCallback.handleError(new ErrorPayload("AUTH_ERROR", "Il nickname non puo' essere vuoto."));
            throw new RemoteException("Invalid authentication payload");
        }

        VirtualViewRMI session = new VirtualViewRMI(this.serverController, clientCallback);

        try {
            this.serverController.authenticate(session.getSessionId(), payload.nickname);

            System.out.println("[RMI] User '" + payload.nickname + "' autenticathed");

            clientCallback.handlePreMatchUpdate(this.payloadMapper.authenticated(payload.nickname));

            return session;

        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("[RMI] Autenticazione fallita per '" + payload.nickname + "': " + e.getMessage());
            session.closeSession();
            clientCallback.handleError(new ErrorPayload("AUTH_ERROR", e.getMessage()));
            throw new RemoteException("Authentication failed: " + e.getMessage());
        }
    }
}
