package it.polimi.ingsoft.mesos.server.model.user;

import it.polimi.ingsoft.mesos.server.controller.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.network.PingPongConnection;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerSessionRegistryTest {

    @Test
    void authenticateStoresAndReturnsCreatedUser() {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        String sessionId = sessions.registerConnection(new DummyConnection());

        User user = sessions.authenticate(sessionId, "Alice");

        assertEquals("Alice", user.getNickname());
        assertEquals(user, sessions.userOf(sessionId));
    }

    @Test
    void authenticateRejectsDuplicateNickname() {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        String firstSessionId = sessions.registerConnection(new DummyConnection());
        String secondSessionId = sessions.registerConnection(new DummyConnection());
        sessions.authenticate(firstSessionId, "Alice");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> sessions.authenticate(secondSessionId, "Alice")
        );

        assertEquals("Nickname already taken: Alice", exception.getMessage());
    }

    @Test
    void unregisterFreesNickname() {
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        String firstSessionId = sessions.registerConnection(new DummyConnection());
        sessions.authenticate(firstSessionId, "Alice");

        sessions.unregister(firstSessionId);

        String secondSessionId = sessions.registerConnection(new DummyConnection());
        User newUser = sessions.authenticate(secondSessionId, "Alice");
        assertEquals("Alice", newUser.getNickname());
    }

    private static class DummyConnection implements PingPongConnection {
        @Override
        public void sendPing() {
        }

        @Override
        public void close() throws IOException {
        }
    }
}
