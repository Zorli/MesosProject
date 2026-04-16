package it.polimi.ingsoft.mesos.client;

import it.polimi.ingsoft.mesos.client.virtualserver.ServerProxyRMI;
import it.polimi.ingsoft.mesos.client.virtualserver.VirtualServerTCP;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.client.view.gui.ClientMain;
import it.polimi.ingsoft.mesos.client.view.tui.TuiController;
import it.polimi.ingsoft.mesos.client.virtualserver.ServerProxy;
import it.polimi.ingsoft.mesos.shared.RmiLoginFacade;
import java.rmi.Naming;
import java.util.Scanner;

/**
 * Entry point that lets the user choose UI mode and network technology before launching the client.
 */
public class LaunchClient {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_TCP_PORT = 12_345;
    private static final int DEFAULT_RMI_PORT = 1099;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Select UI mode:");
        System.out.println("1) GUI");
        System.out.println("2) TUI\n");
        String uiChoice = scanner.nextLine().trim();

        System.out.println("Select network technology:");
        System.out.println("1) TCP");
        System.out.println("2) RMI");
        String networkChoice = scanner.nextLine().trim();

        if (uiChoice.equals("1")) {
            ClientMain.main(args);
        } else {

            try {

                TuiController tuiController = new TuiController();
                ClientView clientView = tuiController;

                ServerProxy serverProxy;

                if (networkChoice.equals("1")) {
                    System.out.println("Connecting to Server via TCP...");
                    serverProxy = new VirtualServerTCP(
                            DEFAULT_HOST,
                            DEFAULT_TCP_PORT,
                            clientView
                    );
                } else if (networkChoice.equals("2")) {
                    System.out.println("Connecting to Server via RMI...");


                    String registryUrl = "rmi://" + DEFAULT_HOST + ":" + DEFAULT_RMI_PORT + "/MesosServer";
                    RmiLoginFacade loginFacade = (RmiLoginFacade) Naming.lookup(registryUrl);


                    serverProxy = new ServerProxyRMI(loginFacade, clientView);
                } else {
                    System.out.println("Scelta di rete non valida. Uscita.");
                    return;
                }


                tuiController.attachServerProxy(serverProxy);
                tuiController.start();

            } catch (Exception exception) {
                throw new RuntimeException("Failed to connect to the server: " + exception.getMessage(), exception);
            }
        }
    }
}