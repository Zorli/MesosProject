package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.server.ServerController;
import it.polimi.ingsoft.mesos.server.virtualview.VirtualViewTCP;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;

public class ConnectionAcceptor implements Runnable{
    private final int port;
    private final ServerController serverController;
    private ServerSocket serverSocket;
    private final MessageRouter router;
    private volatile boolean running;

    public ConnectionAcceptor(int port, ServerController serverController, MessageRouter router) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535");
        }
        this.port = port;
        this.serverController = Objects.requireNonNull(serverController, "serverController cannot be null");
        this.router = Objects.requireNonNull(router, "router cannot be null");
    }

    public synchronized void start() throws IOException {
        if (this.running) {
            throw new IllegalStateException("ConnectionAcceptor is already running");
        }
        this.serverSocket = new ServerSocket(this.port);
        this.running = true;
    }

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

    public synchronized int getPort() {
        if (this.serverSocket == null) {
            return this.port;
        }
        return this.serverSocket.getLocalPort();
    }

}
