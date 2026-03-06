package it.polimi.ingsoft.mesos;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class
Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 320, 240);
        stage.setTitle("Hello!");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        // launch(args);

        System.out.println("Sistema inizializzato. GUI disabilitata. Avvio CLI...");

        // Modello gioco = new Modello();
        // Controller controller = new Controller(gioco);
        // CLI interfacciaTestuale = new CLI(controller);
        // interfacciaTestuale.start();
    }
}
