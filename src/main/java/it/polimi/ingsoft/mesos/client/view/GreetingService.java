package it.polimi.ingsoft.mesos.client.view;

/**
 * Builds user-facing greeting messages for the sample client view.
 */
public class
GreetingService {
    /**
     * Builds a welcome message for the provided name.
     *
     * @param name name to include in the greeting; blank values produce a generic welcome
     * @return greeting string ready to show in the UI
     */
    public String buildGreeting(String name) {
        if (name == null || name.isBlank()) {
            return "Welcome!";
        }

        return "Welcome, " + name.trim() + "!";
    }
}
