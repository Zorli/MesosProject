package it.polimi.ingsoft.mesos.client.controller;

import it.polimi.ingsoft.mesos.client.model.GreetingService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class HelloController {
    private final GreetingService greetingService = new GreetingService();

    @FXML
    private Label welcomeText;

    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText(greetingService.buildGreeting("JavaFX Application"));
    }
}
