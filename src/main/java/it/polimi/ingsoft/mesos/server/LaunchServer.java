package it.polimi.ingsoft.mesos.server;

import it.polimi.ingsoft.mesos.server.controller.preLobby.PreLobbyController;
import it.polimi.ingsoft.mesos.server.factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.helper.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.network.tcp.ConnectionAcceptor;
import it.polimi.ingsoft.mesos.server.helper.MessageRouter;
import it.polimi.ingsoft.mesos.server.network.rmi.RmiConnectionEntryPoint;
import it.polimi.ingsoft.mesos.server.network.connectionHandling.ServerConnectionLifecycle;
import it.polimi.ingsoft.mesos.server.persistence.GameResultRepository;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiConnectionService;

import java.io.PrintStream;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.SQLException;
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
 * <p>Network configuration (TCP/RMI ports and the server IP address) is read
 * interactively from the console at startup. There is no support for {@code .env} files,
 * JVM system properties ({@code -DMESOS_*}) or environment variables: the interactive prompts
 * are the single source of truth for the configuration.</p>
 */
public final class LaunchServer {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        renderInstruction();
        int tcpPort = promptPort(scanner, "Enter MESOS_TCP_PORT [default 12345]: ", 12345);
        int rmiPort = promptPort(scanner, "Enter MESOS_RMI_PORT [default 1099]: ", 1099);
        String serverIp = promptString(scanner, "Enter MESOS_SERVER_IP_ADDRESS [default localhost]: ", "localhost");
        String dbHost = promptString(scanner, "Enter MESOS_DB_HOST [default localhost]: ", "localhost");
        int dbPort = promptPort(scanner, "Enter MESOS_DB_PORT [default 3306]: ", 3306);
        String dbName = promptString(scanner, "Enter MESOS_DB_NAME [default mesos]: ", "mesos");
        String dbUser = promptString(scanner, "Enter MESOS_DB_USER [default mesos]: ", "mesos");
        String dbPassword = promptString(scanner, "Enter MESOS_DB_PASSWORD [default mesos]: ", "mesos");

        Map<String, String> environment = new HashMap<>();
        environment.put("MESOS_TCP_PORT", String.valueOf(tcpPort));
        environment.put("MESOS_RMI_PORT", String.valueOf(rmiPort));
        environment.put("MESOS_SERVER_IP_ADDRESS", serverIp);
        environment.put("MESOS_DB_HOST", dbHost);
        environment.put("MESOS_DB_PORT", String.valueOf(dbPort));
        environment.put("MESOS_DB_NAME", dbName);
        environment.put("MESOS_DB_USER", dbUser);
        environment.put("MESOS_DB_PASSWORD", dbPassword);

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
            // 1. CORE ARCHITECTURE SETUP
            // ==========================================

            // A. Shared Registry (Yellow Pages)
            ServerSessionRegistry sessions = new ServerSessionRegistry();

            // A bis. Ranking database. A failure here is non-fatal: the server keeps
            // running and matches stay playable, the ranking is simply unavailable
            // until the database becomes reachable again.
            GameResultRepository gameResultRepository = new GameResultRepository(
                    buildJdbcUrl(environment),
                    resolveDbUser(environment),
                    resolveDbPassword(environment)
            );
            try {
                gameResultRepository.ensureSchema();
                out.println("Ranking database ready.");
            } catch (SQLException exception) {
                out.println("Warning: ranking database unavailable (" + exception.getMessage()
                        + "). The server continues without persisting rankings.");
            }

            // B. Core Controller
            PreLobbyController preLobbyController = new PreLobbyController(
                    DefaultGameFactory.getInstance(),
                    sessions,
                    gameResultRepository
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
            String serverIp = resolveServerIp(environment);
            startRmiServer(serverConnectionLifecycle, router, rmiPort, serverIp, out);

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

    private static void startRmiServer(ServerConnectionLifecycle serverConnectionLifecycle, MessageRouter router, int port, String serverIp, PrintStream out) throws Exception {
        if (serverIp != null && !serverIp.isBlank()) {
            System.setProperty("java.rmi.server.hostname", serverIp);
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

    private static String resolveServerIp(Map<String, String> environment) {
        return environment.get("MESOS_SERVER_IP_ADDRESS");
    }

    private static String resolveDbUser(Map<String, String> environment) {
        return environment.get("MESOS_DB_USER");
    }

    private static String resolveDbPassword(Map<String, String> environment) {
        return environment.get("MESOS_DB_PASSWORD");
    }

    /**
     * Builds the MySQL JDBC URL from the resolved database configuration.
     *
     * @param environment configuration map holding the {@code MESOS_DB_*} values
     * @return the JDBC URL used to connect to the ranking database
     */
    private static String buildJdbcUrl(Map<String, String> environment) {
        // connectTimeout/socketTimeout bound how long a JDBC call can block. They matter
        // because the end-of-game write runs on the game worker thread right before
        // teardown: if the database becomes unreachable mid-session, the call fails fast
        // instead of hanging the worker.
        return String.format(
                "jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                        + "&connectTimeout=3000&socketTimeout=5000",
                environment.get("MESOS_DB_HOST"),
                environment.get("MESOS_DB_PORT"),
                environment.get("MESOS_DB_NAME"));
    }

    private static int promptPort(Scanner scanner, String promptMessage, int defaultValue) {
        while (true) {
            System.out.print(promptMessage);
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                return defaultValue;
            }
            try {
                int port = Integer.parseInt(input);
                if (port >= 1024 && port <= 65535) {
                    return port;
                }
            } catch (NumberFormatException exception) {
                // Try again below.
            }
            System.out.println("Invalid port. Please enter an integer between 1024 and 65535.");
        }
    }

    private static String promptString(Scanner scanner, String promptMessage, String defaultValue) {
        System.out.print(promptMessage);
        String input = scanner.nextLine().trim();
        return input.isEmpty() ? defaultValue : input;
    }


    private static void renderInstruction() {
        System.out.println("Server network configuration");
        System.out.println();
        System.out.println("The server starts both supported network protocols: TCP and RMI.");
        System.out.println("You must configure these values:");
        System.out.println();
        System.out.println("- MESOS_TCP_PORT: TCP port used by clients that connect with TCP.");
        System.out.println("  It must be an integer between 1024 and 65535 and it must not already be used.");
        System.out.println("- MESOS_RMI_PORT: RMI registry port used by clients that connect with RMI during lookup.");
        System.out.println("  It must be an integer between 1024 and 65535 and it must not already be used.");
        System.out.println("- MESOS_SERVER_IP_ADDRESS: server IP address published inside the RMI stubs.");
        System.out.println("  It is needed only by RMI clients to contact the server after the registry lookup.");
        System.out.println("- MESOS_DB_HOST / MESOS_DB_PORT / MESOS_DB_NAME / MESOS_DB_USER / MESOS_DB_PASSWORD:");
        System.out.println("  connection details of the MySQL database that stores the ranking of finished games.");
        System.out.println("  The defaults match the bundled docker-compose.yml (run 'docker compose up' first).");
        System.out.println("  If the database is unreachable the server still runs, only the ranking is unavailable.");
        System.out.println();
        System.out.println("Press Enter without typing anything to use the default values:");
        System.out.println("- MESOS_TCP_PORT = 12345");
        System.out.println("- MESOS_RMI_PORT = 1099");
        System.out.println("- MESOS_SERVER_IP_ADDRESS = localhost");
        System.out.println("- MESOS_DB_HOST = localhost");
        System.out.println("- MESOS_DB_PORT = 3306");
        System.out.println("- MESOS_DB_NAME = mesos");
        System.out.println("- MESOS_DB_USER = mesos");
        System.out.println("- MESOS_DB_PASSWORD = mesos");
        System.out.println();
    }
}
