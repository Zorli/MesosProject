package it.polimi.ingsoft.mesos.server.network.tcp;

import it.polimi.ingsoft.mesos.server.controller.preLobby.PreLobbyController;
import it.polimi.ingsoft.mesos.server.helper.MessageRouter;
import it.polimi.ingsoft.mesos.server.network.connectionHandling.ServerConnectionLifecycle;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;

/**
 * Accepts TCP client connections for the preLobby.
 * <p>
 * This class is the TCP bootstrap point of the preLobby-side network layer. It
 * owns the listening {@link ServerSocket}, accepts raw client sockets, and
 * creates one {@link VirtualViewTCP} for each accepted connection.
 * <p>
 * The acceptor does not parse client messages and does not execute application
 * commands. After a socket is accepted, the corresponding virtual view becomes
 * responsible for reading the TCP stream, registering the session through the
 * {@link PreLobbyController}, and routing client payloads through the shared
 * {@link MessageRouter}.
 */
public class ConnectionAcceptor implements Runnable{
    private final int port;
    private final ServerConnectionLifecycle serverConnectionLifecycle;
    private ServerSocket serverSocket;
    private final MessageRouter router;
    private volatile boolean running;

    /**
     * Creates a TCP acceptor configured for one listening port.
     *
     * @param port TCP port to bind; {@code 0} lets the operating system choose
     *             an available port
     * @param serverConnectionLifecycle connection manager used by created virtual views to
     *                         register sessions
     * @param router router used by created virtual views to dispatch client
     *               payloads to the correct preLobby phase
     * @throws IllegalArgumentException if {@code port} is outside the valid TCP
     *                                  port range
     * @throws NullPointerException if {@code serverController} or {@code router}
     *                              is null
     */
    public ConnectionAcceptor(int port, ServerConnectionLifecycle serverConnectionLifecycle, MessageRouter router) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535");
        }
        this.port = port;
        this.serverConnectionLifecycle = serverConnectionLifecycle;
        this.router = Objects.requireNonNull(router, "router cannot be null");
    }

    /**
     * Opens the listening preLobby socket.
     * <p>
     * This method prepares the acceptor for {@link #run()}. It must be called
     * before the accept loop starts. If the configured port is {@code 0}, the
     * actual chosen port can be read with {@link #getPort()} after this method
     * returns.
     *
     * @throws IOException if the preLobby socket cannot be opened
     * @throws IllegalStateException if the acceptor is already running
     */
    public synchronized void start() throws IOException {
        if (this.running) {
            throw new IllegalStateException("ConnectionAcceptor is already running");
        }
        this.serverSocket = new ServerSocket(this.port);
        this.running = true;
    }

    /**
     * Runs the TCP accept loop.
     * <p>
     * Each accepted socket is wrapped in a new {@link VirtualViewTCP}. The
     * virtual view is then started on its own thread, so each connected client has
     * an independent TCP reader and writer boundary.
     *
     * @throws IllegalStateException if {@link #start()} was not called before
     *                               running the accept loop, or if accepting a
     *                               connection fails while the acceptor is active
     */
    @Override
    public void run() {
        if (!this.running || this.serverSocket == null) {
            throw new IllegalStateException("ConnectionAcceptor has not been started");
        }

        while (this.running) {
            try {
                Socket clientSocket = this.serverSocket.accept();
                VirtualViewTCP virtualView = new VirtualViewTCP(clientSocket, this.serverConnectionLifecycle, this.router );
                Thread virtualViewThread = new Thread(
                        virtualView,
                        "mesos-virtual-view-" + clientSocket.getPort()
                );
                virtualViewThread.start();
            } catch (IOException exception) {
                if (this.running) {
                    throw new IllegalStateException("TCP accept loop failed", exception);
                }
            }
        }
    }

    /**
     * Returns the TCP port used by this acceptor.
     * <p>
     * Before {@link #start()}, this is the configured port. After the preLobby
     * socket is opened, this is the actual local port bound by the operating
     * system.
     *
     * @return configured or bound TCP port
     */
    public synchronized int getPort() {
        if (this.serverSocket == null) {
            return this.port;
        }
        return this.serverSocket.getLocalPort();
    }

}
