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

        String serverHost = resolveOrPromptString(scanner, "MESOS_SERVER_HOST", "Enter MESOS_SERVER_HOST: ");
        int tcpPort = resolveOrPromptInt(scanner, "MESOS_TCP_PORT", "Enter MESOS_TCP_PORT: ");
        int rmiPort = resolveOrPromptInt(scanner, "MESOS_RMI_PORT", "Enter MESOS_RMI_PORT: ");
        String clientRmiHost = getSystemOrEnv("MESOS_CLIENT_RMI_HOST");

        Map<String, String> environment = new HashMap<>();
        environment.put("MESOS_SERVER_HOST", serverHost);
        environment.put("MESOS_TCP_PORT", String.valueOf(tcpPort));
        environment.put("MESOS_RMI_PORT", String.valueOf(rmiPort));
        if (clientRmiHost != null) {
            environment.put("MESOS_CLIENT_RMI_HOST", clientRmiHost);
        }

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
                        System.setProperty("java.rmi.server.hostname", clientRmiHost);
                    }
                    String registryUrl = "rmi://" + serverHost + ":" + rmiPort + "/MesosServer";
                    RmiConnectionService connectionService = (RmiConnectionService) Naming.lookup(registryUrl);
                    return new ServerProxyRMI(connectionService, clientView);
                }
                System.out.println("Invalid network mode. Please enter 1 or 2.\n");
            } catch (Exception exception) {
                System.out.println("Failed to connect to the server: " + exception.getMessage());
            }
        }
    }

    private static String resolveServerHost(Map<String, String> environment) {
        return environment.get("MESOS_SERVER_HOST");
    }

    private static int resolveTcpPort(Map<String, String> environment) {
        return Integer.parseInt(environment.get("MESOS_TCP_PORT"));
    }

    private static int resolveRmiPort(Map<String, String> environment) {
        return Integer.parseInt(environment.get("MESOS_RMI_PORT"));
    }

    private static String resolveClientRmiHost(Map<String, String> environment) {
        return environment.get("MESOS_CLIENT_RMI_HOST");
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

    private static String resolveOrPromptString(Scanner scanner, String key, String promptMessage) {
        String value = getSystemOrEnv(key);
        if (value != null) {
            return value;
        }

        while (true) {
            System.out.print(promptMessage);
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                return input;
            }
            System.out.println("Value cannot be empty. Please try again.");
        }
    }
}
