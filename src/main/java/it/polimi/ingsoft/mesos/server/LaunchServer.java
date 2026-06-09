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
import java.util.Scanner;


/**
 * Entry point of the Mesos server.
 *
 * <p>The server starts both supported network protocols: TCP and RMI.
 * Both protocols share the same {@link MessageRouter} and the same {@link ServerSessionRegistry},
 * so players using different protocols still enter the same lobby and game system.</p>
 *
 * <p>Network configuration is resolved in layers: an optional {@code server.env} file
 * provides base {@code KEY=value} settings, which are then overridden by JVM system
 * properties or process environment variables of the same name
 * ({@code MESOS_TCP_PORT}, {@code MESOS_RMI_PORT}, {@code MESOS_SERVER_RMI_HOST}).
 * When a value is set in none of these sources, the built-in defaults apply.</p>
 */
public final class LaunchServer {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int tcpPort = resolveOrPromptInt(scanner, "MESOS_TCP_PORT", "Enter MESOS_TCP_PORT: ");
        int rmiPort = resolveOrPromptInt(scanner, "MESOS_RMI_PORT", "Enter MESOS_RMI_PORT: ");
        String serverRmiHost = getSystemOrEnv("MESOS_SERVER_RMI_HOST");

        Map<String, String> environment = new HashMap<>();
        environment.put("MESOS_TCP_PORT", String.valueOf(tcpPort));
        environment.put("MESOS_RMI_PORT", String.valueOf(rmiPort));
        if (serverRmiHost != null) {
            environment.put("MESOS_SERVER_RMI_HOST", serverRmiHost);
        }

        int exitCode = run(environment, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
    /*
     * Server architecture:
     * - RMI/TCP virtual views: handle inbound/outbound connections and the network protocol,
     *   exchanging payloads. A view only receives and sends payloads; internal routing is the
     *   MessageRouter's job.
     * - Protocol-dependent harness around the views: connection acceptor, command handler, etc.
     * - MessageRouter: given a payload, looks up the sender's session snapshot and routes it accordingly.
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
            System.setProperty("java.rmi.server.hostname", serverRmiHost);
        }

        // Pass dependencies down to the RMI entry point
        RmiConnectionService rmiEntryPoint = new RmiConnectionEntryPoint(serverConnectionLifecycle, router);
        LocateRegistry.createRegistry(port);
        Registry registry = LocateRegistry.getRegistry(port);
        registry.rebind("MesosServer", rmiEntryPoint);

        out.println("RMI listener active on port " + port);
    }

    private static int resolveTcpPort(Map<String, String> environment) {
        return Integer.parseInt(environment.get("MESOS_TCP_PORT"));
    }

    private static int resolveRmiPort(Map<String, String> environment) {
        return Integer.parseInt(environment.get("MESOS_RMI_PORT"));
    }

    private static String resolveServerRmiHost(Map<String, String> environment) {
        return environment.get("MESOS_SERVER_RMI_HOST");
    }

    private static String getSystemOrEnv(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }

    private static int resolveOrPromptInt(Scanner scanner, String key, String promptMessage) {
        String value = getSystemOrEnv(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                System.out.println("Invalid value for " + key + " in environment/properties. Please enter manually.");
            }
        }

        while (true) {
            System.out.print(promptMessage);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException exception) {
                System.out.println("Invalid integer. Please try again.");
            }
        }
    }

}
