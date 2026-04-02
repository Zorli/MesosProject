package it.polimi.ingsoft.mesos.server.model.user;

import it.polimi.ingsoft.mesos.server.controller.UserManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserManagerTest {

    @Test
    void registerUserStoresAndReturnsCreatedUser() {
        UserManager userManager = new UserManager();

        User user = userManager.registerUser("Alice");

        assertEquals("Alice", user.getNickname());
        assertSame(user, userManager.getUserByNickname("Alice").orElseThrow());
    }

    @Test
    void registerUserRejectsDuplicateNickname() {
        UserManager userManager = new UserManager();
        userManager.registerUser("Alice");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userManager.registerUser("Alice")
        );

        assertEquals("Nickname already taken: Alice", exception.getMessage());
    }

    @Test
    void getUserByNicknameReturnsEmptyWhenUnknown() {
        UserManager userManager = new UserManager();

        assertTrue(userManager.getUserByNickname("Ghost").isEmpty());
    }
}
