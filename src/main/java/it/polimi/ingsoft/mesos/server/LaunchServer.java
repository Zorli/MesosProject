package it.polimi.ingsoft.mesos.server;

import it.polimi.ingsoft.mesos.server.controller.preLobby.PreLobbyController;
import it.polimi.ingsoft.mesos.server.factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.helper.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.network.tcp.ConnectionAcceptor;
import it.polimi.ingsoft.mesos.server.helper.MessageRouter;
import it.polimi.ingsoft.mesos.server.network.rmi.RmiConnectionEntryPoint;
import it.polimi.ingsoft.mesos.server.network.connectionHandling.ServerConnectionLifecycle;
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
 * Entry point of the Mesos preLobby.
 *
 * <p>The preLobby starts both supported network protocols: TCP and RMI.
 * Both protocols share the same {@link MessageRouter} and the same {@link ServerSessionRegistry},
 * so players using different protocols still enter the same lobby and game system.</p>
 *
 * <p>Network configuration is read only from {@code preLobby.env}. This keeps one
 * explicit source of truth for the ports and RMI address used by the preLobby.</p>
 */
public final class LaunchServer {
    private static final String SERVER_ENV_FILE = "preLobby.env";
    private static final int DEFAULT_TCP_PORT = 12345;
    private static final int DEFAULT_RMI_PORT = 1099;

    public static void main(String[] args) {
        Map<String, String> environment = loadEnvironmentFile(SERVER_ENV_FILE);

        int exitCode = run(environment, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
    /*
    Architettura del preLobby:
    - Virtualview RMI/TCP: Gestione delle connessioni in entrata ed in uscita, si occupano del relativo protocollo di rete
      scambiandosi payload. Quello che la interfaces sa fare è ricevere e mandare in uscita payload, sul come instradarli
      internamente se ne occupa il relativo router
    - Harness vari protocol dependent delle interfaces : connection acceptor, commandhandler, etc
    - Command Router: Dato un payload recuper lo snapshot della sessione del player e lo instrada correttamente
    */
    static int run(Map<String, String> environment, PrintStream out, PrintStream err) {
        try {

            // ==========================================
            // 1. CORE ARCHITECTURE SETUP (The "Big Bang")
            // ==========================================

            // A. Shared Registry (Yellow Pages)
            ServerSessionRegistry sessions = new ServerSessionRegistry();

            // B. Core Controller
            PreLobbyController preLobbyController = new PreLobbyController(
                    DefaultGameFactory.getInstance(),
                    sessions
            );

            Thread serverWorker = new Thread(preLobbyController, "PreLobbyController-Worker");
            serverWorker.start();

            // C. The globally shared Message Router
            MessageRouter router = new MessageRouter(sessions, preLobbyController);

            // D. global Connection Life Cycle manager
            ServerConnectionLifecycle serverConnectionLifecycle = new ServerConnectionLifecycle(
                    sessions
            );

            // ==========================================
            // 2. NETWORK LAYERS SETUP
            // ==========================================

            // Start RMI (Passing the shared router and registry)
            int rmiPort = resolveRmiPort(environment);
            String serverRmiHost = resolveServerRmiHost(environment);
            startRmiServer(serverConnectionLifecycle, router, rmiPort, serverRmiHost, out);

            // Start TCP (Passing the shared router and registry)
            int tcpPort = resolveTcpPort(environment);
            ConnectionAcceptor acceptor = new ConnectionAcceptor(tcpPort, serverConnectionLifecycle, router);
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

    private static void startRmiServer(ServerConnectionLifecycle serverConnectionLifecycle, MessageRouter router, int port, String serverRmiHost, PrintStream out) throws Exception {
        if (serverRmiHost != null && !serverRmiHost.isBlank()) {
            System.setProperty("java.rmi.preLobby.hostname", serverRmiHost);
        }

        // Pass dependencies down to the RMI entry point
        RmiConnectionService rmiEntryPoint = new RmiConnectionEntryPoint(serverConnectionLifecycle, router);
        LocateRegistry.createRegistry(port);
        Registry registry = LocateRegistry.getRegistry(port);
        registry.rebind("MesosServer", rmiEntryPoint);

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
     * <p>The file is optional. If it is missing, the preLobby keeps its default values.
     * Empty lines, comment lines starting with {@code #}, and malformed lines are ignored.</p>
     */
    private static Map<String, String> loadEnvironmentFile(String environmentFile) {
        Map<String, String> environment = new HashMap<>();
        Path environmentPath = Path.of(environmentFile);
        if (!Files.exists(environmentPath)) {
            applyRuntimeOverrides(environment);
            return environment;
        }

        try {
            for (String line : Files.readAllLines(environmentPath)) {
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

        applyRuntimeOverrides(environment);
        return environment;
    }

    private static void applyRuntimeOverrides(Map<String, String> environment) {
        applyRuntimeOverride(environment, "MESOS_TCP_PORT");
        applyRuntimeOverride(environment, "MESOS_RMI_PORT");
        applyRuntimeOverride(environment, "MESOS_SERVER_RMI_HOST");
    }

    private static void applyRuntimeOverride(Map<String, String> environment, String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        if (value != null && !value.isBlank()) {
            environment.put(key, value.trim());
        }
    }

}
