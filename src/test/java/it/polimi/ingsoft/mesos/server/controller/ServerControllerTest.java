package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.user.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests controller-level coordination around active matches.
 */
class ServerControllerTest {

    @Test
    void enterMatchFromLobbyReturnsTheSameOrchestratorForRepeatedLobbyStarts() {
        ServerController controller = new ServerController();
        User alice = controller.authenticate("Alice");
        User bob = controller.authenticate("Bob");
        Lobby lobby = controller.createLobby(alice, 2);
        controller.joinLobby(lobby.getId(), bob);

        GameOrchestrator firstOrchestrator = controller.enterMatchFromLobby(lobby.getId());
        GameOrchestrator secondOrchestrator = controller.enterMatchFromLobby(lobby.getId());

        assertSame(firstOrchestrator, secondOrchestrator);
    }

    @Test
    void createGameFromLobbyDelegatesToTheSharedActiveMatchEntry() {
        ServerController controller = new ServerController();
        User alice = controller.authenticate("Alice");
        User bob = controller.authenticate("Bob");
        Lobby lobby = controller.createLobby(alice, 2);
        controller.joinLobby(lobby.getId(), bob);

        GameOrchestrator firstOrchestrator = controller.createGameFromLobby(lobby.getId());
        GameOrchestrator secondOrchestrator = controller.enterMatchFromLobby(lobby.getId());

        assertSame(firstOrchestrator, secondOrchestrator);
    }

    @Test
    void enterMatchFromLobbyAndCreateGameFromLobbyShareTheSameOrchestrator() {
        ServerController controller = new ServerController();
        User alice = controller.authenticate("Alice");
        User bob = controller.authenticate("Bob");
        Lobby lobby = controller.createLobby(alice, 2);
        controller.joinLobby(lobby.getId(), bob);

        GameOrchestrator firstOrchestrator = controller.enterMatchFromLobby(lobby.getId());
        GameOrchestrator secondOrchestrator = controller.createGameFromLobby(lobby.getId());

        assertSame(firstOrchestrator, secondOrchestrator);
    }

    @Test
    void controllerExposesLobbyLifecycleOperationsThroughItsDefaultManagers() {
        ServerController controller = new ServerController();
        User alice = controller.authenticate("Alice");
        User bob = controller.authenticate("Bob");
        Lobby lobby = controller.createLobby(alice, 2);

        controller.joinLobby(lobby.getId(), bob);

        assertTrue(controller.getLobby(lobby.getId()).isPresent());
        assertFalse(controller.listOpenLobbies().isEmpty());
        assertNotNull(controller.getUserManager());
        assertNotNull(controller.getLobbyManager());
        assertNotNull(controller.createGameFromLobby(lobby.getId()));
    }

    @Test
    void injectedUserManagerConstructorAndExitLobbyRemainUsable() {
        ServerController controller = new ServerController(new UserManager());
        User alice = controller.authenticate("Alice");
        User bob = controller.authenticate("Bob");
        Lobby lobby = controller.createLobby(alice, 2);
        controller.joinLobby(lobby.getId(), bob);

        controller.exitLobby(lobby.getId(), bob);

        assertTrue(controller.getLobby(lobby.getId()).isPresent());
        assertEquals(1, controller.getLobby(lobby.getId()).orElseThrow().getUsers().size());
    }
}
