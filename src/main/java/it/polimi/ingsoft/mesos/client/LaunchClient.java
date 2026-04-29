package it.polimi.ingsoft.mesos.client;

import it.polimi.ingsoft.mesos.client.virtualserver.ServerProxyRMI;
import it.polimi.ingsoft.mesos.client.virtualserver.VirtualServerTCP;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.view.gui.ClientMain;
import it.polimi.ingsoft.mesos.client.virtualserver.ServerProxy;
import it.polimi.ingsoft.mesos.client.view.tui.TuiController;
import it.polimi.ingsoft.mesos.shared.RmiConnectionService;

import java.rmi.Naming;
import java.util.Scanner;

/**
 * Entry point that lets the user choose UI mode and network technology before launching the client.
 */
public class LaunchClient {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_TCP_PORT = 12_345;
    private static final int DEFAULT_RMI_PORT = 1099;

    /**
     * Starts the interactive Mesos client launcher.
     *
     * @param args ignored command-line arguments
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            String uiChoice = readUiChoice(scanner);

            if ("1".equals(uiChoice)) {
                ClientMain.main(args);
                return;
            }

            try {
                TuiController tuiController = new TuiController(scanner, System.out);
                ClientView clientView = tuiController;
                ServerProxy serverProxy;
                String networkChoice = readNetworkChoice(scanner);

                if ("1".equals(networkChoice)) {
                    System.out.println("Connecting to Server via TCP...");
                    serverProxy = new VirtualServerTCP(
                            DEFAULT_HOST,
                            DEFAULT_TCP_PORT,
                            clientView
                    );
                } else {
                    System.out.println("Connecting to Server via RMI...");
                    String registryUrl = "rmi://" + DEFAULT_HOST + ":" + DEFAULT_RMI_PORT + "/MesosServer";
                    RmiConnectionService loginFacade = (RmiConnectionService) Naming.lookup(registryUrl);
                    serverProxy = new ServerProxyRMI(loginFacade, clientView);
                }

                tuiController.attachServerProxy(serverProxy);
                tuiController.start();
                return;
            } catch (Exception exception) {
                System.out.println("Failed to connect to the server: " + exception.getMessage());
            }
        }
    }

    /**
     * Reads and validates the requested UI mode.
     *
     * @param scanner shared launcher scanner
     * @return validated UI selection
     */
    private static String readUiChoice(Scanner scanner) {
        while (true) {
            System.out.println("Select UI mode:");
            System.out.println("1) GUI");
            System.out.println("2) TUI\n");
            String choice = scanner.nextLine().trim();
            if ("1".equals(choice) || "2".equals(choice)) {
                return choice;
            }
            System.out.println("Invalid UI mode. Please enter 1 or 2.\n");
        }
    }

    /**
     * Reads and validates the requested network transport.
     *
     * @param scanner shared launcher scanner
     * @return validated transport selection
     */
    private static String readNetworkChoice(Scanner scanner) {
        while (true) {
            System.out.println("Select network technology:");
            System.out.println("1) TCP");
            System.out.println("2) RMI");
            String choice = scanner.nextLine().trim();
            if ("1".equals(choice) || "2".equals(choice)) {
                return choice;
            }
            System.out.println("Invalid network mode. Please enter 1 or 2.\n");
        }
    }
}
