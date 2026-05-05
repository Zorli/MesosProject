package it.polimi.ingsoft.mesos.server;

import it.polimi.ingsoft.mesos.server.controller.ServerController;
import it.polimi.ingsoft.mesos.server.network.MessageRouter;
import it.polimi.ingsoft.mesos.server.controller.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.model.persistence.ServerRuntimeConfig;
import it.polimi.ingsoft.mesos.server.model.persistence.ServerServices;
import it.polimi.ingsoft.mesos.server.model.persistence.ServerServicesFactory;
import it.polimi.ingsoft.mesos.server.network.ConnectionAcceptor;
import it.polimi.ingsoft.mesos.server.virtualview.RmiSessionFactory;
import it.polimi.ingsoft.mesos.shared.RmiConnectionService;

import java.io.PrintStream;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;

// From this class we bootstrap the server for BOTH TCP and RMI.
// The Singleton MessageRouter and SessionRegistry are shared between both network layers,
// ensuring all players play in the same lobbies regardless of their protocol.
public final class LaunchServer {
    private static final int DEFAULT_TCP_PORT = 12_345;
    private static final int DEFAULT_RMI_PORT = 1099;

    public static void main(String[] args) {
        // IL MAIN ORA È ATTIVO!
        int exitCode = run(System.getenv(), System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(Map<String, String> environment, PrintStream out, PrintStream err) {
        try {
            // ==========================================
            // 1. CORE ARCHITECTURE SETUP (The "Big Bang")
            // ==========================================

            // A. Shared Registry (Yellow Pages)
            ServerSessionRegistry sessions = new ServerSessionRegistry();

            // B. Core Controllers
            ServerController serverController = new ServerController(sessions);
            Thread serverWorker = new Thread(serverController, "ServerController-Worker");
            serverWorker.start();

            // C. The globally shared Message Router
            MessageRouter router = new MessageRouter(sessions, serverController);

            

            // ==========================================
            // 2. NETWORK LAYERS SETUP
            // ==========================================

            // Start RMI (Passing the shared router and registry)
            int rmiPort = resolveRmiPort(environment);
            startRmiServer(serverController, router, sessions, rmiPort, out);

            // Start TCP (Passing the shared router and registry)
            int tcpPort = resolveTcpPort(environment);
            ConnectionAcceptor acceptor = new ConnectionAcceptor(tcpPort, serverController, router);
            Thread acceptorThread = new Thread(acceptor, "mesos-connection-acceptor");
            acceptorThread.start();

            out.println("TCP listener active on port " + tcpPort);
            out.println("Mesos Server started successfully.");
            out.println("Waiting for players...");
            return 0;

        } catch (Exception exception) {
            err.println("Server startup failed.");
            exception.printStackTrace(err);
            return 1;
        }
    }

    private static void startRmiServer(ServerController controller, MessageRouter router, ServerSessionRegistry sessions, int port, PrintStream out) throws Exception {
        // Pass dependencies down to the RMI Factory
        RmiConnectionService rmiFactory = new RmiSessionFactory(controller, router);
        LocateRegistry.createRegistry(port);
        Registry registry = LocateRegistry.getRegistry(port);
        registry.rebind("MesosServer", rmiFactory);

        out.println("RMI listener active on port " + port);
    }

    private static int resolveTcpPort(Map<String, String> environment) {
        if (environment == null) {
            return DEFAULT_TCP_PORT;
        }
        String rawPort = environment.get("MESOS_TCP_PORT");
        if (rawPort == null || rawPort.isBlank()) {
            return DEFAULT_TCP_PORT;
        }
        try {
            return Integer.parseInt(rawPort);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("MESOS_TCP_PORT must be an integer", exception);
        }
    }

    private static int resolveRmiPort(Map<String, String> environment) {
        if (environment == null) {
            return DEFAULT_RMI_PORT;
        }
        String rawPort = environment.get("MESOS_RMI_PORT");
        if (rawPort == null || rawPort.isBlank()) {
            return DEFAULT_RMI_PORT;
        }
        try {
            return Integer.parseInt(rawPort);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("MESOS_RMI_PORT must be an integer", exception);
        }
    }
}