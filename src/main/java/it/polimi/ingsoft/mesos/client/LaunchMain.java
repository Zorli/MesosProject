package it.polimi.ingsoft.mesos.client;

import it.polimi.ingsoft.mesos.client.view.gui.ClientMain;
import it.polimi.ingsoft.mesos.client.view.tui.TuiController;

import java.util.Scanner;

/**
 * Entry point that lets the user choose UI mode and network technology before launching the client.
 */
public class LaunchMain {

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

        // TODO: wire network choice to virtual server instantiation

        if (uiChoice.equals("1")) {
            ClientMain.main(args);
        } else {
            TuiController tuiController = new TuiController();
            tuiController.start();
        }
    }
}
