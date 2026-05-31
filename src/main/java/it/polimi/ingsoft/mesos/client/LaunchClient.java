package it.polimi.ingsoft.mesos.client;

import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxy;
import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxyRMI;
import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxyTCP;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.view.gui.GUIView;
import it.polimi.ingsoft.mesos.client.view.tui.TuiView;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiConnectionService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.Naming;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Entry point that lets the user choose UI mode and network technology before launching the client.
 */
public class LaunchClient {
    private static final String CLIENT_ENV_FILE = "client.env";
    private static final String DEFAULT_SERVER_HOST = "localhost";
    private static final int DEFAULT_TCP_PORT = 12_345;
    private static final int DEFAULT_RMI_PORT = 1099;

    // Architecture overview:
    // - ClientView: UI contract; exposes methods the network layer calls to show errors, lobbies, and game state.
    // - ServerProxy: contract used by the UI controller to send commands to the preLobby (TCP and RMI implementations).
    // - VirtualServer: contract used by the network layer to deliver preLobby messages to the client. Delegates
    //   routing to MessageRouter rather than deciding which view method to call directly.
    // - MessageRouter: sits between VirtualServer and ClientView; reads the ServerMessage type and dispatches
    //   the correct view method, keeping VirtualServer focused on transport only.
    //
    // Data flow: UI controller -> ServerProxy -> preLobby -> VirtualServer -> MessageRouter -> ClientView

    public static void main(String[] args) {
        Map<String, String> environment = loadEnvironmentFile(CLIENT_ENV_FILE);
        Scanner scanner = new Scanner(System.in);

        ClientView clientView = chooseClientView(scanner);
        ServerProxy serverProxy = chooseServerProxy(scanner, clientView, environment);

        startClient(clientView, serverProxy);
    }

    private static ClientView chooseClientView(Scanner scanner) {
        while (true) {
            System.out.println("Select UI mode:");
            System.out.println("1) GUI");
            System.out.println("2) TUI");

            String choice = scanner.nextLine().trim();
            if ("1".equals(choice)) {
                return new GUIView();
            }
            if ("2".equals(choice)) {
                return new TuiView(scanner, System.out);
            }

            System.out.println("Invalid UI mode. Please enter 1 or 2.\n");
        }
    }

    private static ServerProxy chooseServerProxy(Scanner scanner, ClientView clientView, Map<String, String> environment) {
        String serverHost = resolveServerHost(environment);
        int tcpPort = resolveTcpPort(environment);
        int rmiPort = resolveRmiPort(environment);
        String clientRmiHost = resolveClientRmiHost(environment);

        while (true) {
            System.out.println("Select network technology:");
            System.out.println("1) TCP");
            System.out.println("2) RMI");

            String choice = scanner.nextLine().trim();
            try {
                if ("1".equals(choice)) {
                    return new ServerProxyTCP(serverHost, tcpPort, clientView);
                }
                if ("2".equals(choice)) {
                    if (clientRmiHost != null) {
                        System.setProperty("java.rmi.preLobby.hostname", clientRmiHost);
                    }
                    String registryUrl = "rmi://" + serverHost + ":" + rmiPort + "/MesosServer";
                    RmiConnectionService connectionService = (RmiConnectionService) Naming.lookup(registryUrl);
                    return new ServerProxyRMI(connectionService, clientView);
                }
                System.out.println("Invalid network mode. Please enter 1 or 2.\n");
            } catch (Exception exception) {
                System.out.println("Failed to connect to the preLobby: " + exception.getMessage());
            }
        }
    }

    private static String resolveServerHost(Map<String, String> environment) {
        if (environment == null) {
            return DEFAULT_SERVER_HOST;
        }
        String rawHost = environment.get("MESOS_SERVER_HOST");
        if (rawHost == null || rawHost.isBlank()) {
            return DEFAULT_SERVER_HOST;
        }
        return rawHost.trim();
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

    private static String resolveClientRmiHost(Map<String, String> environment) {
        if (environment == null) {
            return null;
        }
        String rawHost = environment.get("MESOS_CLIENT_RMI_HOST");
        if (rawHost == null || rawHost.isBlank()) {
            return null;
        }
        return rawHost.trim();
    }

    private static void startClient(ClientView clientView, ServerProxy serverProxy) {
        if (clientView instanceof TuiView tuiController) {
            tuiController.attachServerProxy(serverProxy);
            tuiController.start();
            return;
        }

        if (clientView instanceof GUIView guiController) {
            guiController.attachServerProxy(serverProxy);
            guiController.start();
        }
    }
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
            System.err.println("Failed to load environment file: " + environmentFile);
        }

        applyRuntimeOverrides(environment);
        return environment;
    }

    private static void applyRuntimeOverrides(Map<String, String> environment) {
        applyRuntimeOverride(environment, "MESOS_SERVER_HOST");
        applyRuntimeOverride(environment, "MESOS_TCP_PORT");
        applyRuntimeOverride(environment, "MESOS_RMI_PORT");
        applyRuntimeOverride(environment, "MESOS_CLIENT_RMI_HOST");
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
