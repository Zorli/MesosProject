package it.polimi.ingsoft.mesos.server;

import it.polimi.ingsoft.mesos.server.factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.controller.server.ServerController;
import it.polimi.ingsoft.mesos.server.controller.server.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.network.ConnectionAcceptor;
import it.polimi.ingsoft.mesos.server.network.MessageRouter;
import it.polimi.ingsoft.mesos.server.virtualview.RmiConnectionEntryPoint;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiConnectionService;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.Map;


/**
 * Entry point of the Mesos server.
 *
 * <p>The server starts both supported network protocols: TCP and RMI.
 * Both protocols share the same {@link MessageRouter} and the same {@link ServerSessionRegistry},
 * so players using different protocols still enter the same lobby and game system.</p>
 *
 * <p>Network configuration is read only from {@code server.env}. This keeps one
 * explicit source of truth for the ports and RMI address used by the server.</p>
 */
public final class LaunchServer {
    private static final String SERVER_ENV_FILE = "server.env";
    private static final int DEFAULT_TCP_PORT = 12345;
    private static final int DEFAULT_RMI_PORT = 1099;
    private static final int DEFAULT_RMI_OBJECT_PORT = 1100;

    public static void main(String[] args) {
        Map<String, String> environment = loadEnvironmentFile(SERVER_ENV_FILE);

        int exitCode = run(environment, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
    /*
    Architettura del server:
    - Virtualview RMI/TCP: Gestione delle connessioni in entrata ed in uscita, si occupano del relativo protocollo di rete
      scambiandosi payload. Quello che la virtualview sa fare è ricevere e mandare in uscita payload, sul come instradarli
      internamente se ne occupa il relativo router
    - Harness vari protocol dependent delle virtualview : connection acceptor, commandhandler, etc
    - Command Router: Dato un payload recuper lo snapshot della sessione del player e lo instrada correttamente
    */
    static int run(Map<String, String> environment, PrintStream out, PrintStream err) {
        try {

            // ==========================================
            // 1. CORE ARCHITECTURE SETUP (The "Big Bang")
            // ==========================================

            // A. Shared Registry (Yellow Pages)
            ServerSessionRegistry sessions = new ServerSessionRegistry();

            // B. Core Controllers
            ServerController serverController = new ServerController(
                    DefaultGameFactory.getInstance(),
                    sessions
            );

            Thread serverWorker = new Thread(serverController, "ServerController-Worker");
            serverWorker.start();

            // C. The globally shared Message Router
            MessageRouter router = new MessageRouter(sessions, serverController);

            // ==========================================
            // 2. NETWORK LAYERS SETUP
            // ==========================================

            // Start RMI (Passing the shared router and registry)
            int rmiPort = resolveRmiPort(environment);
            int rmiObjectPort = resolveRmiObjectPort(environment);
            String serverRmiHost = resolveServerRmiHost(environment);
            startRmiServer(serverController, router, rmiPort, rmiObjectPort, serverRmiHost, out);

            // Start TCP (Passing the shared router and registry)
            int tcpPort = resolveTcpPort(environment);
            ConnectionAcceptor acceptor = new ConnectionAcceptor(tcpPort, serverController, router);
            acceptor.start();
            Thread acceptorThread = new Thread(acceptor, "mesos-connection-acceptor");
            acceptorThread.start();

            out.println("TCP listener active on port " + acceptor.getPort());
            out.println("Mesos Server started successfully.");
            out.println("Waiting for players...");
            return 0;

        } catch (Exception exception) {
            err.println("Server startup failed.");
            exception.printStackTrace(err);
            return 1;
        }
    }

    private static void startRmiServer(ServerController controller, MessageRouter router, int port, int objectPort, String serverRmiHost, PrintStream out) throws Exception {
        out.println("[RMI-DEBUG][SERVER] starting RMI registryPort=" + port + ", objectPort=" + objectPort + ", host=" + serverRmiHost);
        if (serverRmiHost != null && !serverRmiHost.isBlank()) {
            System.setProperty("java.rmi.server.hostname", serverRmiHost);
            out.println("[RMI-DEBUG][SERVER] java.rmi.server.hostname=" + System.getProperty("java.rmi.server.hostname"));
        }

        // Pass dependencies down to the RMI entry point
        RmiConnectionService rmiEntryPoint = new RmiConnectionEntryPoint(controller, router, objectPort);
        out.println("[RMI-DEBUG][SERVER] RmiConnectionEntryPoint exported");
        LocateRegistry.createRegistry(port);
        out.println("[RMI-DEBUG][SERVER] registry created on port " + port);
        Registry registry = LocateRegistry.getRegistry(port);
        registry.rebind("MesosServer", rmiEntryPoint);
        out.println("[RMI-DEBUG][SERVER] MesosServer bound in registry");

        out.println("RMI listener active on port " + port);
        out.println("RMI objects exported on port " + objectPort);
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

    private static int resolveRmiObjectPort(Map<String, String> environment) {
        if (environment == null) {
            return DEFAULT_RMI_OBJECT_PORT;
        }
        String rawPort = environment.get("MESOS_RMI_OBJECT_PORT");
        if (rawPort == null || rawPort.isBlank()) {
            return DEFAULT_RMI_OBJECT_PORT;
        }
        try {
            return Integer.parseInt(rawPort);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("MESOS_RMI_OBJECT_PORT must be an integer", exception);
        }
    }

    private static String resolveServerRmiHost(Map<String, String> environment) {
        if (environment == null) {
            return null;
        }
        String rawHost = environment.get("MESOS_SERVER_RMI_HOST");
        if (rawHost == null || rawHost.isBlank()) {
            return null;
        }
        return rawHost.trim();
    }

    /**
     * Loads a simple {@code KEY=value} environment file.
     *
     * <p>The file is optional. If it is missing, the server keeps its default values.
     * Empty lines, comment lines starting with {@code #}, and malformed lines are ignored.</p>
     */
    private static Map<String, String> loadEnvironmentFile(String environmentFile) {
        Map<String, String> environment = new HashMap<>();
        try {
            for (String line : Files.readAllLines(Path.of(environmentFile))) {
                line = line.trim();
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("=", 2);
                if (parts.length != 2) {
                    continue;
                }
                environment.put(parts[0].trim(), parts[1].trim());
            }
        } catch (IOException ignored) {
            System.err.println("Unable to load environment file: " + environmentFile);
        }

        return environment;
    }

}
