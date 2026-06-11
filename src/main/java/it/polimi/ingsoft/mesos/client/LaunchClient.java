package it.polimi.ingsoft.mesos.client;

import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxy;
import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxyRMI;
import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxyTCP;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.view.gui.GUIView;
import it.polimi.ingsoft.mesos.client.view.tui.TuiView;
import it.polimi.ingsoft.mesos.shared.RmiNetworkInterfaces.RmiConnectionService;

import java.rmi.Naming;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Entry point that lets the user choose UI mode and network technology before launching the client.
 *
 * <p>All network configuration (server IP address, TCP/RMI ports and the client IP address)
 * is read interactively from the console at startup. There is no support for {@code .env} files,
 * JVM system properties ({@code -DMESOS_*}) or environment variables: the interactive prompts are
 * the single source of truth for the configuration.</p>
 */
public class LaunchClient {

    // Architecture overview:
    // - ClientView: UI contract; exposes methods the network layer calls to show errors, lobbies, and game state.
    // - ServerProxy: contract used by the UI controller to send commands to the server (TCP and RMI implementations).
    // - VirtualServer: contract used by the network layer to deliver server messages to the client. Delegates
    //   routing to MessageRouter rather than deciding which view method to call directly.
    // - MessageRouter: sits between VirtualServer and ClientView; reads the ServerMessage type and dispatches
    //   the correct view method, keeping VirtualServer focused on transport only.
    //
    // Data flow: UI controller -> ServerProxy -> server -> VirtualServer -> MessageRouter -> ClientView

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        renderInstruction();
        String serverIpAddress = promptString(scanner, "Enter MESOS_SERVER_IP_ADDRESS [default localhost]: ", "localhost");
        int tcpPort = promptPort(scanner, "Enter MESOS_TCP_PORT [default 12345]: ", 12345);
        int rmiPort = promptPort(scanner, "Enter MESOS_RMI_PORT [default 1099]: ", 1099);
        String clientIpAddress = promptString(scanner, "Enter MESOS_CLIENT_IP_ADDRESS [default localhost]: ", "localhost");

        Map<String, String> environment = new HashMap<>();
        environment.put("MESOS_SERVER_IP_ADDRESS", serverIpAddress);
        environment.put("MESOS_TCP_PORT", String.valueOf(tcpPort));
        environment.put("MESOS_RMI_PORT", String.valueOf(rmiPort));
        environment.put("MESOS_CLIENT_IP_ADDRESS", clientIpAddress);

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
        String serverIpAddress = resolveServerIpAddress(environment);
        int tcpPort = resolveTcpPort(environment);
        int rmiPort = resolveRmiPort(environment);
        String clientIpAddress = resolveClientIpAddress(environment);

        while (true) {
            System.out.println("Select network technology:");
            System.out.println("1) TCP");
            System.out.println("2) RMI");

            String choice = scanner.nextLine().trim();
            try {
                if ("1".equals(choice)) {
                    return new ServerProxyTCP(serverIpAddress, tcpPort, clientView);
                }
                if ("2".equals(choice)) {
                    if (clientIpAddress != null && !clientIpAddress.isBlank()) {
                        System.setProperty("java.rmi.server.hostname", clientIpAddress);
                    }
                    String registryUrl = "rmi://" + serverIpAddress + ":" + rmiPort + "/MesosServer";
                    RmiConnectionService connectionService = (RmiConnectionService) Naming.lookup(registryUrl);
                    return new ServerProxyRMI(connectionService, clientView);
                }
                System.out.println("Invalid network mode. Please enter 1 or 2.\n");
            } catch (Exception exception) {
                System.out.println("Failed to connect to the server: " + exception.getMessage());
            }
        }
    }

    private static String resolveServerIpAddress(Map<String, String> environment) {
        return environment.get("MESOS_SERVER_IP_ADDRESS");
    }

    private static int resolveTcpPort(Map<String, String> environment) {
        return Integer.parseInt(environment.get("MESOS_TCP_PORT"));
    }

    private static int resolveRmiPort(Map<String, String> environment) {
        return Integer.parseInt(environment.get("MESOS_RMI_PORT"));
    }

    private static String resolveClientIpAddress(Map<String, String> environment) {
        return environment.get("MESOS_CLIENT_IP_ADDRESS");
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
        System.out.println("Client network configuration");
        System.out.println();
        System.out.println("The client can connect to the server through TCP or RMI.");
        System.out.println("You must configure these values:");
        System.out.println();
        System.out.println("- MESOS_SERVER_IP_ADDRESS: server IP address used by the client to contact the server.");
        System.out.println("  It is used by both TCP and RMI.");
        System.out.println("- MESOS_TCP_PORT: TCP port exposed by the server.");
        System.out.println("  It is used if you choose TCP and must match the server TCP port.");
        System.out.println("- MESOS_RMI_PORT: RMI registry port exposed by the server.");
        System.out.println("  It is used if you choose RMI and must match the server RMI port.");
        System.out.println("- MESOS_CLIENT_IP_ADDRESS: client IP address published inside the RMI callback stub.");
        System.out.println("  It is needed only if you choose RMI, so the server can contact the client callback.");
        System.out.println();
        System.out.println("Press Enter without typing anything to use the default values:");
        System.out.println("- MESOS_SERVER_IP_ADDRESS = localhost");
        System.out.println("- MESOS_TCP_PORT = 12345");
        System.out.println("- MESOS_RMI_PORT = 1099");
        System.out.println("- MESOS_CLIENT_IP_ADDRESS = localhost");
        System.out.println();
    }
}
