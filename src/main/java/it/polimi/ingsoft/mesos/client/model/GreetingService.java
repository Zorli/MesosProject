package it.polimi.ingsoft.mesos.client.model;

public class
GreetingService {
    public String buildGreeting(String name) {
        if (name == null || name.isBlank()) {
            return "Welcome!";
        }

        return "Welcome, " + name.trim() + "!";
    }
}
