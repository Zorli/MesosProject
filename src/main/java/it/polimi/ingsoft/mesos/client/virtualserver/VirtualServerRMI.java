package it.polimi.ingsoft.mesos.client.virtualserver;

import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxyRMI;
import it.polimi.ingsoft.mesos.client.view.MessageRouter;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiClientService;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.messages.ServerMessageType;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Objects;

/**
 * RMI implementation of the server-to-client communication path.
 * <p>
 * In the RMI client architecture, {@link ServerProxyRMI} and {@code VirtualServerRMI} own the two opposite
 * directions of the same logical connection:
 * <ul>
 *     <li>{@link ServerProxyRMI} calls the remote server session to send client commands;</li>
 *     <li>{@code VirtualServerRMI} is exported as a remote callback object so the server can call the client
 *     back with updates.</li>
 * </ul>
 * <p>
 * This class is the RMI equivalent of {@link VirtualServerTCP}. TCP receives server messages by reading a socket;
 * RMI receives server messages because the server invokes methods on this exported callback object.
 * <p>
 * The class does not contain UI logic or game logic. It receives already-built {@link ServerMessage} objects from
 * the server and delegates routing to {@link MessageRouter}, which then calls the appropriate {@link ClientView}
 * method.
 */
public class VirtualServerRMI implements RmiClientService {

    private final MessageRouter messageRouter;
    private final boolean exported;

    /**
     * Creates and exports the client callback object used by the RMI server.
     * <p>
     * Exporting makes this object reachable from another JVM through Java RMI. After the object is exported, the
     * server can call {@link #handleMessage(ServerMessage)} and {@link #handlePing()} remotely.
     *
     * @param clientView view contract used to display server updates on the client
     * @throws RemoteException if the callback object cannot be exported
     */
    public VirtualServerRMI(ClientView clientView) throws RemoteException {
        this(clientView, true);
    }

    public VirtualServerRMI(ClientView clientView, int callbackPort) throws RemoteException {
        this(clientView, true, callbackPort);
    }

    /**
     * Creates the client callback object, optionally exporting it through Java RMI.
     * <p>
     * Used code exports the object. Tests can pass {@code false} to avoid opening a real RMI export while
     * still exercising the routing behavior.
     *
     * @param clientView view contract used to display server updates on the client
     * @param exported   whether this object must be exported through {@link UnicastRemoteObject}
     * @throws RemoteException if export is requested and fails
     */
    public VirtualServerRMI(ClientView clientView, boolean exported) throws RemoteException {
        this(clientView, exported, 0);
    }

    public VirtualServerRMI(ClientView clientView, boolean exported, int callbackPort) throws RemoteException {
        if (exported) {
            System.out.println("[RMI-DEBUG][CLIENT] exporting callback on port " + callbackPort);
            UnicastRemoteObject.exportObject(this, callbackPort);
            System.out.println("[RMI-DEBUG][CLIENT] callback exported on port " + callbackPort);
        }
        this.messageRouter = new MessageRouter(Objects.requireNonNull(clientView, "clientView cannot be null"));
        this.exported = exported;
    }

    /**
     * Receives one server-to-client message through the RMI callback.
     * <p>
     * Application messages are routed to the view through {@link MessageRouter}. PING is handled separately
     * because it belongs to the connection heartbeat protocol and must produce a PONG response through
     * {@link ServerProxyRMI}, not a UI update.
     *
     * @param message message sent by the remote server
     */
    public void handleMessage(ServerMessage message) {
        System.out.println("[RMI-DEBUG][CLIENT] callback handleMessage " + this.describeMessage(message));
        if (message.type == ServerMessageType.PING) {
            this.handlePing();
            return;
        }

        this.messageRouter.routeMessage(message);
        System.out.println("[RMI-DEBUG][CLIENT] routed " + this.describeMessage(message));
    }

    /**
     * @param serverProxy proxy connected to the same RMI server session
     */
    public void attachServerProxy(ServerProxyRMI serverProxy) {
        Objects.requireNonNull(serverProxy, "serverProxy cannot be null");
        System.out.println("[RMI-DEBUG][CLIENT] attachServerProxy called");
    }

    /**
     * Handles the RMI heartbeat request sent by the server.
     * <p>
     * PING is not shown to the user. In RMI, receiving this callback is already enough for the server to know
     * that the client callback object is reachable.
     */
    @Override
    public void handlePing() {
        System.out.println("[RMI-DEBUG][CLIENT] callback handlePing");
    }

    /**
     * Releases the RMI callback object exported for this client.
     * <p>
     * Only exported instances must be unexported. Test instances created with {@code exported == false} do not
     * own an RMI export and therefore have nothing to release.
     *
     * @throws NoSuchObjectException if RMI cannot find this exported object during unexport
     */
    public void close() throws NoSuchObjectException {
        if (this.exported) {
            System.out.println("[RMI-DEBUG][CLIENT] unexporting callback");
            UnicastRemoteObject.unexportObject(this, true);
            System.out.println("[RMI-DEBUG][CLIENT] callback unexported");
        }
    }

    private String describeMessage(ServerMessage message) {
        if (message == null) {
            return "null";
        }
        Object payload = message.payload;
        return message.type + "/" + (payload == null ? "null" : payload.getClass().getSimpleName());
    }
}
