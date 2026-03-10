package it.polimi.ingsoft.mesos.client.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GreetingServiceTest {
    private final GreetingService greetingService = new GreetingService();

    @Test
    void buildGreetingReturnsDefaultMessageWhenNameIsMissing() {
        assertEquals("Welcome!", greetingService.buildGreeting(""));
    }

    @Test
    void buildGreetingReturnsPersonalizedMessageWhenNameIsPresent() {
        assertEquals("Welcome, Alice!", greetingService.buildGreeting(" Alice "));
    }
}
