package it.polimi.ingsoft.mesos.server.model.user;

import it.polimi.ingsoft.mesos.server.model.server.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTest {

    @Test
    void constructorRejectsNullAndBlankNicknames() {
        assertThrows(IllegalArgumentException.class, () -> new User(null));
        assertThrows(IllegalArgumentException.class, () -> new User(" "));
    }

    @Test
    void constructorCreatesUserWithNickname() {
        User user = new User("Alice");

        assertEquals("Alice", user.getNickname());
    }

    @Test
    void equalityDependsOnNicknameOnly() {
        User first = new User("Alice");
        User second = new User("Alice");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }
}
