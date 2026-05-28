package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.server.ServerController;
import it.polimi.ingsoft.mesos.server.virtualview.VirtualViewTCP;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;

/**
 * Accepts TCP client connections for the server.
 * <p>
 * This class is the TCP bootstrap point of the server-side network layer. It
 * owns the listening {@link ServerSocket}, accepts raw client sockets, and
 * creates one {@link VirtualViewTCP} for each accepted connection.
 * <p>
 * The acceptor does not parse client messages and does not execute application
 * commands. After a socket is accepted, the corresponding virtual view becomes
 * responsible for reading the TCP stream, registering the session through the
 * {@link ServerController}, and routing client payloads through the shared
 * {@link MessageRouter}.
 */
public class ConnectionAcceptor implements Runnable{
    private final int port;
    private final ServerController serverController;
    private ServerSocket serverSocket;
    private final MessageRouter router;
    private volatile boolean running;

    /**
     * Creates a TCP acceptor configured for one listening port.
     *
     * @param port TCP port to bind; {@code 0} lets the operating system choose
     *             an available port
     * @param serverController controller used by created virtual views to
     *                         register sessions
     * @param router router used by created virtual views to dispatch client
     *               payloads to the correct server phase
     * @throws IllegalArgumentException if {@code port} is outside the valid TCP
     *                                  port range
     * @throws NullPointerException if {@code serverController} or {@code router}
     *                              is null
     */
    public ConnectionAcceptor(int port, ServerController serverController, MessageRouter router) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535");
        }
        this.port = port;
        this.serverController = Objects.requireNonNull(serverController, "serverController cannot be null");
        this.router = Objects.requireNonNull(router, "router cannot be null");
    }

    /**
     * Opens the listening server socket.
     * <p>
     * This method prepares the acceptor for {@link #run()}. It must be called
     * before the accept loop starts. If the configured port is {@code 0}, the
     * actual chosen port can be read with {@link #getPort()} after this method
     * returns.
     *
     * @throws IOException if the server socket cannot be opened
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
                VirtualViewTCP virtualView = new VirtualViewTCP(clientSocket, this.serverController, this.router );
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
     * Before {@link #start()}, this is the configured port. After the server
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
