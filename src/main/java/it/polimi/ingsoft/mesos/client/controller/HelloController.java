package it.polimi.ingsoft.mesos.client.controller;

import it.polimi.ingsoft.mesos.client.view.GreetingService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * JavaFX controller for the sample welcome view.
 */
public class HelloController {
    private final GreetingService greetingService = new GreetingService();

    @FXML
    private Label welcomeText;

    /**
     * Handles the hello-button action by populating the greeting label.
     */
    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText(greetingService.buildGreeting("JavaFX Application"));
    }
}
