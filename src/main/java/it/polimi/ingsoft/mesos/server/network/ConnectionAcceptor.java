package it.polimi.ingsoft.mesos.server.network;

import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.virtualview.VirtualViewTCP;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;

public class ConnectionAcceptor implements Runnable{
    private final int port;
    private final ServerController serverController;
    private ServerSocket serverSocket;
    private volatile boolean running;

    public ConnectionAcceptor(int port, ServerController serverController) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535");
        }
        this.port = port;
        this.serverController = Objects.requireNonNull(serverController, "serverController cannot be null");
    }

    @Override
    public void run() {
        try {
            synchronized (this) {
                if (this.running) {
                    throw new IllegalStateException("ConnectionAcceptor is already running");
                }
                this.serverSocket = new ServerSocket(this.port);
                this.running = true;
            }

            while (this.running) {
                try {
                    Socket clientSocket = this.serverSocket.accept();
                    VirtualViewTCP virtualView = new VirtualViewTCP(clientSocket, this.serverController);
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
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to open TCP listener on port " + this.port, exception);
        }
    }

    public synchronized int getPort() {
        if (this.serverSocket == null) {
            return this.port;
        }
        return this.serverSocket.getLocalPort();
    }

}
