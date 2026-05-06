package it.polimi.ingsoft.mesos.client.view.gui;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * JavaFX entry point for the Mesos client application.
 */
public class ClientMain extends Application {
    @Override
    public void start(Stage stage) {
        new GUIController().start(stage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
