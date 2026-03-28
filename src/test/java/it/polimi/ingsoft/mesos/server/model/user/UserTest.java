package it.polimi.ingsoft.mesos.server.model.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    @Test
    void constructorRejectsNullAndBlankNicknames() {
        assertThrows(IllegalArgumentException.class, () -> new User(null));
        assertThrows(IllegalArgumentException.class, () -> new User(" "));
    }

    @Test
    void constructorCreatesOnlineUserWithNickname() {
        User user = new User("Alice");

        assertEquals("Alice", user.getNickname());
        assertTrue(user.isOnline());
    }

    @Test
    void setOnlineUpdatesConnectionState() {
        User user = new User("Alice");

        user.setOnline(false);

        assertFalse(user.isOnline());
    }

    @Test
    void equalityDependsOnNicknameOnly() {
        User first = new User("Alice");
        User second = new User("Alice");

        second.setOnline(false);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }
}
