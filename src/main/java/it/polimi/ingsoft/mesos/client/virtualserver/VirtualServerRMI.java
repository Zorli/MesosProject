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
 * RMI implementation of the preLobby-to-client communication path.
 * <p>
 * In the RMI client architecture, {@link ServerProxyRMI} and {@code VirtualServerRMI} own the two opposite
 * directions of the same logical connection:
 * <ul>
 *     <li>{@link ServerProxyRMI} calls the remote preLobby session to send client commands;</li>
 *     <li>{@code VirtualServerRMI} is exported as a remote callback object so the preLobby can call the client
 *     back with updates.</li>
 * </ul>
 * <p>
 * This class is the RMI equivalent of {@link VirtualServerTCP}. TCP receives preLobby messages by reading a socket;
 * RMI receives preLobby messages because the preLobby invokes methods on this exported callback object.
 * <p>
 * The class does not contain UI logic or game logic. It receives already-built {@link ServerMessage} objects from
 * the preLobby and delegates routing to {@link MessageRouter}, which then calls the appropriate {@link ClientView}
 * method.
 */
public class VirtualServerRMI implements RmiClientService {

    private final MessageRouter messageRouter;
    private final boolean exported;
    private ServerProxyRMI serverProxy;

    /**
     * Creates and exports the client callback object used by the RMI preLobby.
     * <p>
     * Exporting makes this object reachable from another JVM through Java RMI. After the object is exported, the
     * preLobby can call {@link #handleMessage(ServerMessage)} and {@link #handlePing()} remotely.
     *
     * @param clientView view contract used to display preLobby updates on the client
     * @throws RemoteException if the callback object cannot be exported
     */
    public VirtualServerRMI(ClientView clientView) throws RemoteException {
        this(clientView, true);
    }

    /**
     * Creates the client callback object, optionally exporting it through Java RMI.
     * <p>
     * Used code exports the object. Tests can pass {@code false} to avoid opening a real RMI export while
     * still exercising the routing behavior.
     *
     * @param clientView view contract used to display preLobby updates on the client
     * @param exported   whether this object must be exported through {@link UnicastRemoteObject}
     * @throws RemoteException if export is requested and fails
     */
    public VirtualServerRMI(ClientView clientView, boolean exported) throws RemoteException {
        if (exported) {
            UnicastRemoteObject.exportObject(this, 0);
        }
        this.messageRouter = new MessageRouter(Objects.requireNonNull(clientView, "clientView cannot be null"));
        this.exported = exported;
    }

    /**
     * Receives one preLobby-to-client message through the RMI callback.
     * <p>
     * Application messages are routed to the view through {@link MessageRouter}. PING is handled separately
     * because it belongs to the connection heartbeat protocol and must produce a PONG response through
     * {@link ServerProxyRMI}, not a UI update.
     *
     * @param message message sent by the remote preLobby
     */
    public void handleMessage(ServerMessage message) {
        if (message.type == ServerMessageType.PING) {
            this.handlePing();
            return;
        }

        this.messageRouter.routeMessage(message);
    }

    /**
     * Connects this preLobby-to-client callback with the matching client-to-preLobby proxy.
     * <p>
     * The reference is needed only for heartbeat handling: when the preLobby sends a PING callback, this object
     * answers by asking {@link ServerProxyRMI} to send PONG to the remote preLobby session.
     *
     * @param serverProxy proxy connected to the same RMI preLobby session
     */
    public void attachServerProxy(ServerProxyRMI serverProxy) {
        this.serverProxy = Objects.requireNonNull(serverProxy, "serverProxy cannot be null");
    }

    /**
     * Handles the RMI heartbeat request sent by the preLobby.
     * <p>
     * PING is not shown to the user. The callback answers by using the attached {@link ServerProxyRMI} to send
     * PONG on the client-to-preLobby direction of the RMI session.
     *
     * @throws IllegalStateException if the callback has not yet been attached to its proxy
     */
    @Override
    public void handlePing() {
        if (this.serverProxy == null) {
            throw new IllegalStateException("Cannot reply to ping before connection");
        }
        this.serverProxy.sendPong();
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
            UnicastRemoteObject.unexportObject(this, true);
        }
    }
}
